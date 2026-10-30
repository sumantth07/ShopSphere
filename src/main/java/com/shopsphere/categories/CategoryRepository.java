package com.shopsphere.categories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    interface CategoryCount { Long getId(); String getName(); String getSlug(); long getProductCount(); }

    @Query("""
        select c.id as id, c.name as name, c.slug as slug, count(p) as productCount
        from Category c left join Product p on p.category = c and p.active = true
        group by c.id, c.name, c.slug order by count(p) desc, c.name""")
    List<CategoryCount> findAllWithCounts();
}
