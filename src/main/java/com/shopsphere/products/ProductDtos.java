package com.shopsphere.products;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductSummary(Long id, String name, String brand, String category, String categorySlug,
                                 BigDecimal price, String imageUrl, BigDecimal rating, Integer reviewCount,
                                 boolean inStock, int stockQuantity, String stockSource, String sellerName) {
        static ProductSummary of(Product p) {
            return new ProductSummary(p.getId(), p.getName(), p.getBrand(), p.getCategory().getName(),
                p.getCategory().getSlug(), p.getPrice(), p.getImageUrl(), p.getRating(), p.getReviewCount(),
                p.isListingAvailable() && p.getStockQuantity() > 0, p.getStockQuantity(), p.getStockSource(),
                p.getSellerName());
        }
    }

    public record ProductDetail(ProductSummary summary, String description, List<String> images, boolean listingAvailable) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                                  boolean hasNext, boolean hasPrevious) {
        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                p.getTotalPages(), p.hasNext(), p.hasPrevious());
        }
    }

    public record ProductCompare(long id, String name, String brand, String category, java.math.BigDecimal price, String imageUrl,
                                 java.math.BigDecimal rating, Integer reviewCount, boolean inStock, int stockQuantity, String sellerName,
                                 String description, int imageCount) {}

    public record BrandResponse(String brand, long productCount) {}
}
