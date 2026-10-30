package com.shopsphere.products;

import com.shopsphere.exception.NotFoundException;
import com.shopsphere.products.ProductDtos.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final int maxPageSize;
    private final int defaultPageSize;

    public ProductService(ProductRepository products, ProductImageRepository images,
                          @Value("${shopsphere.catalog.max-page-size}") int maxPageSize,
                          @Value("${shopsphere.catalog.default-page-size}") int defaultPageSize) {
        this.products = products; this.images = images; this.maxPageSize = maxPageSize; this.defaultPageSize = defaultPageSize;
    }

    public PageResponse<ProductSummary> search(ProductSpecs.Filter filter, Integer page, Integer size, String sort, String dir) {
        int safePage = Math.max(page == null ? 0 : page, 0);
        int safeSize = size == null ? defaultPageSize : Math.min(Math.max(size, 1), maxPageSize);
        Pageable pageable = PageRequest.of(safePage, safeSize, ProductSort.resolve(sort, dir));
        return PageResponse.of(products.findAll(ProductSpecs.from(filter), pageable).map(ProductSummary::of));
    }

    public ProductDetail get(Long id) {
        Product p = products.findByIdAndActiveTrue(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        List<String> extra = images.findByProductIdOrderByPositionAsc(id).stream().map(ProductImage::getUrl).toList();
        List<String> all = new java.util.ArrayList<>();
        all.add(p.getImageUrl());
        extra.stream().filter(u -> !u.equals(p.getImageUrl())).forEach(all::add);
        return new ProductDetail(ProductSummary.of(p), p.getDescription(), all, p.isListingAvailable());
    }

    public List<ProductCompare> compare(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.size() < 2 || distinct.size() > 4) throw new IllegalArgumentException("Choose between 2 and 4 products to compare.");
        List<Product> found = products.findAllById(distinct).stream().filter(Product::isActive).toList();
        if (found.size() != distinct.size()) throw new NotFoundException("One or more products were not found.");
        return distinct.stream().map(id -> found.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow()).map(p -> {
            ProductSummary s = ProductSummary.of(p);
            String d = p.getDescription();
            int imgs = 1 + images.findByProductIdOrderByPositionAsc(p.getId()).size();
            return new ProductCompare(p.getId(), p.getName(), p.getBrand(), s.category(), p.getPrice(), p.getImageUrl(), p.getRating(),
                p.getReviewCount(), s.inStock(), p.getStockQuantity(), s.sellerName(), d == null ? null : (d.length() > 400 ? d.substring(0, 400) + "…" : d), imgs);
        }).toList();
    }

    public List<BrandResponse> topBrands(int limit) {
        return products.topBrands(PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
            .stream().map(b -> new BrandResponse(b.getBrand(), b.getProductCount())).toList();
    }
}
