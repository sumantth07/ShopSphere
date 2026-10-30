package com.shopsphere.products;

import com.shopsphere.products.ProductDtos.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    /** GET /api/products?q=&categoryId=&brand=&minPrice=&maxPrice=&inStock=&minRating=&page=&size=&sort=&dir= */
    @GetMapping("/api/products")
    public PageResponse<ProductSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir) {
        if (q != null && q.length() > 100) throw new IllegalArgumentException("q must be at most 100 characters");
        return service.search(new ProductSpecs.Filter(q, categoryId, brand, minPrice, maxPrice, inStock, minRating),
            page, size, sort, dir);
    }

    @GetMapping("/api/products/{id}")
    public ProductDetail get(@PathVariable Long id) { return service.get(id); }

    /** GET /api/products/compare?ids=1,2,3 (2 to 4 products). Only catalog facts; missing values stay null. */
    @GetMapping("/api/products/compare")
    public List<ProductCompare> compare(@RequestParam List<Long> ids) { return service.compare(ids); }

    @GetMapping("/api/brands")
    public List<BrandResponse> brands(@RequestParam(defaultValue = "40") int limit) { return service.topBrands(limit); }
}
