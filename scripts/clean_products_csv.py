#!/usr/bin/env python3
"""Clean products_rows.csv for PostgreSQL staging. Standard library only.

Usage: python3 clean_products_csv.py <input.csv> <clean_out.csv> <rejects_out.csv> <report_out.json>

Fixes (without touching legitimate text):
  * image values wrapped in literal double quotes   -> unwrapped
  * mojibake (UTF-8 decoded as cp1252, e.g. 'â€™')  -> repaired only when the round trip succeeds
  * HTML entities (&amp; &#39; ...)                 -> unescaped
  * more_images JSON array                          -> validated, re-serialised compactly
Rows with invalid id / name / price / image / available are written to the rejects
file with the reason. They are never silently dropped.
"""
import csv, html, json, re, sys
from collections import Counter

# A lead byte (Ã, Â, â) followed by a cp1252-rendered continuation byte = typical mojibake.
MOJI = re.compile("[\u00c2\u00c3\u00e2][\u0080-\u00bf\u20ac\u201a-\u203a\u0152\u0153\u0160\u0161\u0178\u017d\u017e\u0192\u02c6\u02dc\u2122]")


def _to_bytes(s: str) -> bytes:
    """cp1252 where defined; fall back to latin-1 for the 5 bytes cp1252 leaves undefined (0x81,0x8D,0x8F,0x90,0x9D)."""
    out = bytearray()
    for ch in s:
        try:
            out += ch.encode("cp1252")
        except UnicodeEncodeError:
            out += ch.encode("latin-1")
    return bytes(out)


def fix_text(s: str) -> str:
    s = s.strip()
    if MOJI.search(s):
        try:
            s = _to_bytes(s).decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            pass  # leave untouched rather than corrupt
    return html.unescape(s).strip()


def unwrap(s: str) -> str:
    s = s.strip()
    while len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1].strip()
    return s


def main(src, out, rej, rep):
    rows = list(csv.DictReader(open(src, encoding="utf-8-sig", newline="")))
    cols = list(rows[0].keys())
    seen, good, bad, stats = set(), [], [], Counter()
    for n, r in enumerate(rows, start=2):  # line 1 = header
        errs = []
        rid = r["id"].strip()
        if not rid.isdigit():
            errs.append("invalid id")
        if rid in seen:
            errs.append("duplicate id")
        name = fix_text(r["name"])
        if not name:
            errs.append("empty name")
        try:
            price = float(r["price"])
            assert price > 0
        except Exception:
            errs.append("invalid price")
            price = None
        image = unwrap(r["image"])
        if not re.match(r"^https?://", image):
            errs.append("invalid image url")
        try:
            raw = unwrap(r["more_images"]) if r["more_images"].strip() else "[]"
            more = [u for u in json.loads(raw) if isinstance(u, str) and re.match(r"^https?://", u)]
        except Exception:
            more = []
            stats["malformed more_images (set to [])"] += 1
        try:
            rating = float(r["rating"])
            rating = rating if 0 <= rating <= 5 else None
        except Exception:
            rating = None
        if rating is None:
            stats["invalid rating (set to NULL)"] += 1
        try:
            rc = int(float(r["review_count"]))
            rc = rc if rc >= 0 else None
        except Exception:
            rc = None
        if rc is None:
            stats["invalid review_count (set to NULL)"] += 1
        avail = r["available"].strip().lower()
        if avail not in ("true", "false"):
            errs.append("invalid available")
        if errs:
            bad.append({"line": n, "id": rid, "errors": "; ".join(errs), **r})
            continue
        seen.add(rid)
        desc = fix_text(r["description"])
        if not desc:
            stats["empty description"] += 1
        if desc != r["description"].strip():
            stats["description text repaired (mojibake/entities)"] += 1
        good.append({
            "id": rid, "name": name, "description": desc, "price": f"{price:.2f}",
            "category": fix_text(r["category"]), "image": image, "seller": fix_text(r["seller"]),
            "created_at": r["created_at"].strip(), "available": avail, "brand": fix_text(r["brand"]),
            "review_count": "" if rc is None else rc, "rating": "" if rating is None else rating,
            "more_images": json.dumps(more, separators=(",", ":")),
        })
    with open(out, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        w.writerows(good)
    with open(rej, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["line", "id", "errors"] + cols)
        w.writeheader()
        w.writerows(bad)
    report = {
        "input_rows": len(rows), "clean_rows": len(good), "rejected_rows": len(bad),
        "adjustments": dict(stats),
        "distinct_categories": len({g["category"] for g in good}),
        "distinct_sellers": len({g["seller"] for g in good}),
        "distinct_brands": len({g["brand"] for g in good if g["brand"]}),
        "available_true": sum(g["available"] == "true" for g in good),
        "available_false": sum(g["available"] == "false" for g in good),
        "same_name_and_brand_pairs_kept_distinct_ids": len(good) - len({(g["name"], g["brand"]) for g in good}),
    }
    json.dump(report, open(rep, "w"), indent=2)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main(*sys.argv[1:5])
