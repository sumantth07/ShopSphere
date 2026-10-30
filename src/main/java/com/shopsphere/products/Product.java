package com.shopsphere.products;

import com.shopsphere.categories.Category;
import jakarta.persistence.*;
import org.hibernate.annotations.Formula;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "source_id") private Long sourceId;
    @Column(nullable = false) private String name;
    private String description;
    @Column(nullable = false) private BigDecimal price;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id")
    private Category category;
    private String brand;
    @Column(name = "image_url", nullable = false) private String imageUrl;
    @Column(name = "imported_seller_name") private String importedSellerName;
    @Column(name = "listing_available", nullable = false) private boolean listingAvailable;
    @Column(name = "stock_quantity", nullable = false) private int stockQuantity;
    @Column(name = "stock_source", nullable = false) private String stockSource;
    private BigDecimal rating;
    @Column(name = "review_count") private Integer reviewCount;
    @Column(nullable = false) private boolean active;
    @Column(name = "seller_id") private Long sellerId;
    @Formula("(select s.store_name from sellers s where s.id = seller_id)") private String sellerName;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected Product() {}
    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
    public String getImportedSellerName() { return importedSellerName; }
    public boolean isListingAvailable() { return listingAvailable; }
    public int getStockQuantity() { return stockQuantity; }
    public String getStockSource() { return stockSource; }
    public BigDecimal getRating() { return rating; }
    public Integer getReviewCount() { return reviewCount; }
    public boolean isActive() { return active; }
    public Long getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName != null ? sellerName : importedSellerName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
