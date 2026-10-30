package com.shopsphere.products;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(nullable = false) private String url;
    private int position;

    protected ProductImage() {}
    public String getUrl() { return url; }
    public int getPosition() { return position; }
}
