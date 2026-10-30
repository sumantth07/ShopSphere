package com.shopsphere.categories;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    public record CategoryResponse(Long id, String name, String slug, long productCount) {}

    private final CategoryRepository repo;
    public CategoryController(CategoryRepository repo) { this.repo = repo; }

    @GetMapping
    public List<CategoryResponse> list() {
        return repo.findAllWithCounts().stream()
            .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getProductCount())).toList();
    }
}
