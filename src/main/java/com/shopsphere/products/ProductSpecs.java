package com.shopsphere.products;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds filters with the JPA Criteria API. All values are bound parameters, so no SQL injection surface. */
public final class ProductSpecs {
    private ProductSpecs() {}

    public record Filter(String q, Long categoryId, String brand, BigDecimal minPrice, BigDecimal maxPrice,
                         Boolean inStock, BigDecimal minRating) {}

    public static Specification<Product> from(Filter f) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category"); // avoid N+1 for list pages (not applied to the count query)
            }
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.get("active")));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + escapeLike(f.q().trim().toLowerCase()) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                            cb.like(cb.lower(root.get("brand")), like, '\\')));
            }
            if (f.categoryId() != null) p.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            if (f.brand() != null && !f.brand().isBlank()) p.add(cb.equal(cb.lower(root.get("brand")), f.brand().trim().toLowerCase()));
            if (f.minPrice() != null) p.add(cb.greaterThanOrEqualTo(root.get("price"), f.minPrice()));
            if (f.maxPrice() != null) p.add(cb.lessThanOrEqualTo(root.get("price"), f.maxPrice()));
            if (Boolean.TRUE.equals(f.inStock())) {
                p.add(cb.isTrue(root.get("listingAvailable")));
                p.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }
            if (f.minRating() != null) p.add(cb.greaterThanOrEqualTo(root.get("rating"), f.minRating()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
