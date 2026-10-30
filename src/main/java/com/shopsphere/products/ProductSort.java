package com.shopsphere.products;

import java.util.Map;
import org.springframework.data.domain.Sort;

/** Whitelist of sortable fields: clients send a key, never a raw property name. */
public final class ProductSort {
    private ProductSort() {}

    private static final Map<String, String> FIELDS = Map.of(
        "newest", "createdAt", "price", "price", "rating", "rating", "reviews", "reviewCount", "name", "name");

    public static Sort resolve(String key, String direction) {
        String field = FIELDS.get(key == null ? "newest" : key);
        if (field == null) throw new IllegalArgumentException("Unsupported sort '" + key + "'. Allowed: " + FIELDS.keySet());
        boolean desc = direction == null ? !"name".equals(key) && !"price".equals(key) : "desc".equalsIgnoreCase(direction);
        if (direction != null && !desc && !"asc".equalsIgnoreCase(direction))
            throw new IllegalArgumentException("direction must be 'asc' or 'desc'");
        Sort.Order main = desc ? Sort.Order.desc(field) : Sort.Order.asc(field);
        if (field.equals("rating") || field.equals("reviewCount")) main = main.nullsLast();
        return Sort.by(main, Sort.Order.desc("id")); // id tiebreaker keeps pagination stable
    }
}
