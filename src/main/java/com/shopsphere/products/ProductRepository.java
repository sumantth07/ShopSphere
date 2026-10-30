package com.shopsphere.products;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndActiveTrue(Long id);

    @Query("select p.brand as brand, count(p) as productCount from Product p " +
           "where p.active = true and p.brand is not null group by p.brand order by count(p) desc, p.brand")
    List<BrandCount> topBrands(Pageable pageable);

    interface BrandCount { String getBrand(); long getProductCount(); }
}
