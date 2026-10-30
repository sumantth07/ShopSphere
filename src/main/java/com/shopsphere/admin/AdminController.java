package com.shopsphere.admin;

import com.shopsphere.exception.ApiException;
import com.shopsphere.orders.OrderService;
import com.shopsphere.sellers.SellerController;
import com.shopsphere.sellers.SellerController.ProductRequest;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Every route here is protected by hasRole("ADMIN") in SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record SellerRow(long id, String storeName, String status, String origin, String contactEmail, String ownerName, String ownerEmail, long productCount, OffsetDateTime createdAt, String description, String rejectionReason) {}
    public record SellerDecision(@NotNull String status, @Size(max = 500) String reason) {}
    public record CategoryRequest(@NotBlank @Size(max = 100) String name) {}
    public record ProductRow(long id, String name, String brand, String category, BigDecimal price, int stockQuantity, boolean active, String sellerName, Long sellerId) {}
    public record AdminProductRequest(@NotNull Long sellerId, @Valid @NotNull ProductRequest product) {}
    public record AdminOrderRow(long id, String status, OffsetDateTime createdAt, BigDecimal total, String customerName, String paymentStatus, int sellerGroups, boolean demo) {}

    private final JdbcClient jdbc; private final OrderService orders; private final SellerController sellerApi;
    public AdminController(JdbcClient jdbc, OrderService orders, SellerController sellerApi) { this.jdbc = jdbc; this.orders = orders; this.sellerApi = sellerApi; }

    private void audit(Jwt jwt, String action, String type, Object id, String detail) {
        jdbc.sql("insert into audit_logs (actor_user_id, action, target_type, target_id, detail) values (:a,:ac,:t,:i,:d)")
            .param("a", CurrentUser.id(jwt)).param("ac", action).param("t", type).param("i", String.valueOf(id)).param("d", detail, Types.VARCHAR).update();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        var m = new LinkedHashMap<String, Object>();
        m.put("products", jdbc.sql("select count(*) from products").query(Long.class).single());
        m.put("activeProducts", jdbc.sql("select count(*) from products where active").query(Long.class).single());
        m.put("categories", jdbc.sql("select count(*) from categories").query(Long.class).single());
        m.put("orders", jdbc.sql("select count(*) from orders").query(Long.class).single());
        m.put("customers", jdbc.sql("select count(*) from users where role = 'CUSTOMER'").query(Long.class).single());
        m.put("pendingSellerApplications", jdbc.sql("select count(*) from sellers where origin = 'REGISTERED' and application_status = 'PENDING'").query(Long.class).single());
        m.put("approvedSellers", jdbc.sql("select count(*) from sellers where origin = 'REGISTERED' and application_status = 'APPROVED'").query(Long.class).single());
        m.put("ordersByStatus", jdbc.sql("select status, count(*) from orders group by status").query((rs, n) -> Map.of("status", rs.getString(1), "count", rs.getLong(2))).list());
        // Sales = value of ACTIVE order items (purchase-time prices, excluding shipping); cancelled items excluded.
        m.put("salesTotal", jdbc.sql("select coalesce(sum(unit_price*quantity),0) from order_items where status='ACTIVE'").query(BigDecimal.class).single());
        m.put("salesTrend", jdbc.sql("""
            select to_char(g.day,'YYYY-MM-DD'), coalesce(x.revenue,0), coalesce(x.orders,0) from generate_series(current_date - 29, current_date, interval '1 day') g(day)
            left join (select o.created_at::date d, sum(i.unit_price*i.quantity) filter (where i.status='ACTIVE') revenue, count(distinct o.id) orders from orders o join order_items i on i.order_id=o.id group by 1) x on x.d = g.day::date order by g.day""")
            .query((rs, n) -> Map.of("date", rs.getString(1), "revenue", rs.getBigDecimal(2), "orders", rs.getLong(3))).list());
        m.put("recentOrders", ordersPage(null, 0, 5).content());
        return m;
    }

    // ---------------- sellers ----------------
    @GetMapping("/sellers")
    public List<SellerRow> sellers(@RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean includeImported) {
        return jdbc.sql("""
            select s.id, s.store_name, s.application_status, s.origin, s.contact_email, u.full_name, u.email, (select count(*) from products p where p.seller_id = s.id), s.created_at, s.store_description, s.rejection_reason
            from sellers s left join users u on u.id = s.user_id where (:imp or s.origin = 'REGISTERED') and (cast(:st as text) is null or s.application_status = :st)
            order by (s.application_status = 'PENDING') desc, s.created_at desc limit 300""")
            .param("imp", includeImported).param("st", status == null || status.isBlank() ? null : status, Types.VARCHAR)
            .query((rs, n) -> new SellerRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8),
                rs.getObject(9, OffsetDateTime.class), rs.getString(10), rs.getString(11))).list();
    }

    /** APPROVED / REJECTED / SUSPENDED. Suspending deactivates the seller's listings so nothing can be bought. */
    @PatchMapping("/sellers/{id}") @Transactional
    public Map<String, String> decide(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody SellerDecision r) {
        if (!List.of("APPROVED", "REJECTED", "SUSPENDED").contains(r.status())) throw ApiException.badRequest("STATUS_INVALID", "Status must be APPROVED, REJECTED or SUSPENDED.");
        if (r.status().equals("REJECTED") && (r.reason() == null || r.reason().isBlank())) throw ApiException.badRequest("REASON_REQUIRED", "Please give a reason for rejecting.");
        var row = jdbc.sql("select user_id, origin, store_name from sellers where id = :id for update").param("id", id)
            .query((rs, n) -> new Object[]{(Long) rs.getObject(1), rs.getString(2), rs.getString(3)}).optional().orElseThrow(() -> ApiException.notFound("Seller not found."));
        if ("IMPORTED".equals(row[1])) throw ApiException.badRequest("IMPORTED_SELLER", "Imported catalog sellers have no account and cannot be changed here.");
        jdbc.sql("update sellers set application_status=:s, rejection_reason=:r, reviewed_by=:a, reviewed_at=now(), updated_at=now() where id=:id")
            .param("s", r.status()).param("r", r.status().equals("REJECTED") ? r.reason() : null, Types.VARCHAR).param("a", CurrentUser.id(jwt)).param("id", id).update();
        if (r.status().equals("SUSPENDED")) jdbc.sql("update products set active=false, updated_at=now() where seller_id=:id").param("id", id).update();
        String msg = switch (r.status()) {
            case "APPROVED" -> "Your seller application for \"" + row[2] + "\" was approved. You can now open the seller dashboard.";
            case "REJECTED" -> "Your seller application for \"" + row[2] + "\" was rejected. Reason: " + r.reason();
            default -> "Your seller account \"" + row[2] + "\" has been suspended." + (r.reason() == null ? "" : " Reason: " + r.reason());
        };
        jdbc.sql("insert into notifications (user_id, type, message) values (:u, :t, :m)").param("u", row[0]).param("t", "SELLER_" + r.status()).param("m", msg).update();
        audit(jwt, "SELLER_" + r.status(), "seller", id, r.reason());
        return Map.of("status", r.status());
    }

    // ---------------- categories ----------------
    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return jdbc.sql("select c.id, c.name, c.slug, count(p.id) from categories c left join products p on p.category_id = c.id group by c.id order by c.name")
            .query((rs, n) -> Map.<String, Object>of("id", rs.getLong(1), "name", rs.getString(2), "slug", rs.getString(3), "productCount", rs.getLong(4))).list();
    }
    static String slug(String s) { return s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""); }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest r) {
        if (slug(r.name()).isEmpty()) throw ApiException.badRequest("NAME_INVALID", "Category name must contain letters or numbers.");
        try {
            long id = jdbc.sql("insert into categories (name, slug) values (:n,:s) returning id").param("n", r.name().trim()).param("s", slug(r.name())).query(Long.class).single();
            audit(jwt, "CATEGORY_CREATE", "category", id, r.name()); return Map.of("id", id);
        } catch (org.springframework.dao.DuplicateKeyException e) { throw ApiException.conflict("CATEGORY_EXISTS", "A category with that name already exists."); }
    }
    @PutMapping("/categories/{id}")
    public Map<String, Object> renameCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody CategoryRequest r) {
        try {
            if (jdbc.sql("update categories set name=:n, slug=:s where id=:id").param("n", r.name().trim()).param("s", slug(r.name())).param("id", id).update() == 0) throw ApiException.notFound("Category not found.");
        } catch (org.springframework.dao.DuplicateKeyException e) { throw ApiException.conflict("CATEGORY_EXISTS", "A category with that name already exists."); }
        audit(jwt, "CATEGORY_RENAME", "category", id, r.name()); return Map.of("id", id);
    }
    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        if (jdbc.sql("select exists(select 1 from products where category_id=:id)").param("id", id).query(Boolean.class).single())
            throw ApiException.conflict("CATEGORY_IN_USE", "This category still has products. Move or deactivate them first.");
        if (jdbc.sql("delete from categories where id=:id").param("id", id).update() == 0) throw ApiException.notFound("Category not found.");
        audit(jwt, "CATEGORY_DELETE", "category", id, null);
    }

    // ---------------- products ----------------
    @GetMapping("/products")
    public Page<ProductRow> products(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String where = " where (cast(:like as text) is null or lower(p.name) like :like or lower(p.brand) like :like)";
        long total = jdbc.sql("select count(*) from products p" + where).param("like", like, Types.VARCHAR).query(Long.class).single();
        var rows = jdbc.sql("select p.id, p.name, p.brand, c.name, p.price, p.stock_quantity, p.active, coalesce(s.store_name, p.imported_seller_name), p.seller_id from products p join categories c on c.id=p.category_id left join sellers s on s.id=p.seller_id"
                + where + " order by p.id desc limit :l offset :o").param("like", like, Types.VARCHAR).param("l", sz).param("o", pg * sz)
            .query((rs, n) -> new ProductRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getInt(6), rs.getBoolean(7), rs.getString(8), (Long) rs.getObject(9))).list();
        return new Page<>(rows, pg, sz, total, (int) Math.ceil(total / (double) sz));
    }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String, Long> createProduct(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdminProductRequest r) {
        if (!jdbc.sql("select exists(select 1 from sellers where id=:s and (origin='IMPORTED' or application_status='APPROVED'))").param("s", r.sellerId()).query(Boolean.class).single())
            throw ApiException.badRequest("SELLER_INVALID", "Choose an approved seller.");
        long id = sellerApi.insertProduct(r.sellerId(), r.product()); audit(jwt, "PRODUCT_CREATE", "product", id, null); return Map.of("id", id);
    }
    @PatchMapping("/products/{id}/status")
    public Map<String, Object> setActive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        if (jdbc.sql("update products set active=:a, updated_at=now() where id=:id").param("a", active).param("id", id).update() == 0) throw ApiException.notFound("Product not found.");
        audit(jwt, active ? "PRODUCT_ACTIVATE" : "PRODUCT_DEACTIVATE", "product", id, null); return Map.of("id", id, "active", active);
    }
    @PatchMapping("/products/{id}/stock")
    public Map<String, Object> setStock(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Map<String, Integer> body) {
        Integer q = body.get("stockQuantity");
        if (q == null || q < 0 || q > 1_000_000) throw ApiException.badRequest("STOCK_INVALID", "Stock must be a whole number from 0 to 1,000,000.");
        if (jdbc.sql("update products set stock_quantity=:q, stock_source='SELLER', updated_at=now() where id=:id").param("q", q).param("id", id).update() == 0) throw ApiException.notFound("Product not found.");
        audit(jwt, "PRODUCT_STOCK", "product", id, String.valueOf(q)); return Map.of("id", id, "stockQuantity", q);
    }

    // ---------------- orders ----------------
    private Page<AdminOrderRow> ordersPage(String status, int page, int size) {
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        String st = status == null || status.isBlank() ? null : status;
        long total = jdbc.sql("select count(*) from orders where (cast(:st as text) is null or status = :st)").param("st", st, Types.VARCHAR).query(Long.class).single();
        var rows = jdbc.sql("""
            select o.id, o.status, o.created_at, o.total, u.full_name, p.status, (select count(*) from seller_orders s where s.order_id=o.id), o.is_demo
            from orders o join users u on u.id=o.user_id left join payments p on p.order_id=o.id where (cast(:st as text) is null or o.status = :st)
            order by o.created_at desc limit :l offset :o""").param("st", st, Types.VARCHAR).param("l", sz).param("o", pg * sz)
            .query((rs, n) -> new AdminOrderRow(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class), rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBoolean(8))).list();
        return new Page<>(rows, pg, sz, total, (int) Math.ceil(total / (double) sz));
    }
    @GetMapping("/orders")
    public Page<AdminOrderRow> orders(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ordersPage(status, page, size); }

    @GetMapping("/orders/{id}")
    public Map<String, Object> order(@PathVariable long id) {
        var head = jdbc.sql("select o.id, o.status, o.created_at, o.total, u.full_name, u.email, p.status, p.method, concat_ws(', ', o.ship_name, o.ship_line1, o.ship_city, o.ship_state, o.ship_postal) from orders o join users u on u.id=o.user_id left join payments p on p.order_id=o.id where o.id=:id")
            .param("id", id).query((rs, n) -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getLong(1)); m.put("status", rs.getString(2)); m.put("createdAt", rs.getObject(3, OffsetDateTime.class)); m.put("total", rs.getBigDecimal(4));
                m.put("customerName", rs.getString(5)); m.put("customerEmail", rs.getString(6)); m.put("paymentStatus", rs.getString(7)); m.put("paymentMethod", rs.getString(8)); m.put("shipTo", rs.getString(9));
                return m; }).optional().orElseThrow(() -> ApiException.notFound("Order not found."));
        head.put("groups", orders.groupsFor("so.order_id = :id", id));
        return head;
    }
    /** Admin may advance any shipment (needed for imported sellers, who have no login). */
    @PostMapping("/shipments/{groupId}/advance")
    public Map<String, String> advance(@AuthenticationPrincipal Jwt jwt, @PathVariable long groupId) {
        String s = orders.advance(groupId); audit(jwt, "SHIPMENT_ADVANCE", "seller_order", groupId, s); return Map.of("status", s);
    }

    // ---------------- customers ----------------
    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@RequestParam(required = false) String q) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("select id, full_name, email, email_verified, status, created_at, (select count(*) from orders o where o.user_id = u.id) from users u where role='CUSTOMER' and (cast(:like as text) is null or lower(full_name) like :like or email like :like) order by created_at desc limit 100")
            .param("like", like, Types.VARCHAR).query((rs, n) -> Map.<String, Object>of("id", rs.getLong(1), "fullName", rs.getString(2), "email", rs.getString(3), "emailVerified", rs.getBoolean(4), "status", rs.getString(5),
                "createdAt", rs.getObject(6, OffsetDateTime.class).toString(), "orders", rs.getLong(7))).list();
    }
}
