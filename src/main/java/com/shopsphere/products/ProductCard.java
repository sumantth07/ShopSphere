package com.shopsphere.products;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Lightweight product projection used by cart, wishlist, comparison and chatbot SQL. */
public record ProductCard(long id, String name, String brand, String category, BigDecimal price, String imageUrl,
                          BigDecimal rating, Integer reviewCount, boolean inStock, int stockQuantity, String sellerName, Long sellerId) {
    public static final String COLS = """
        p.id, p.name, p.brand, c.name as category, p.price, p.image_url, p.rating, p.review_count,
        (p.active and p.listing_available and p.stock_quantity > 0) as in_stock, p.stock_quantity,
        coalesce(s.store_name, p.imported_seller_name) as seller_name, p.seller_id""";
    public static final String FROM = " from products p join categories c on c.id = p.category_id left join sellers s on s.id = p.seller_id ";

    public static ProductCard map(ResultSet rs, int n) throws SQLException {
        return new ProductCard(rs.getLong("id"), rs.getString("name"), rs.getString("brand"), rs.getString("category"), rs.getBigDecimal("price"),
            rs.getString("image_url"), rs.getBigDecimal("rating"), (Integer) rs.getObject("review_count"), rs.getBoolean("in_stock"),
            rs.getInt("stock_quantity"), rs.getString("seller_name"), (Long) rs.getObject("seller_id"));
    }
}
