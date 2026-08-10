package com.example.cachefileapi.repository;

import com.example.cachefileapi.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String keyword);

    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    List<Product> findByStockQuantityGreaterThanEqual(Integer minStock);

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT p.id AS id, p.name AS name FROM Product p ORDER BY p.name ASC")
    List<Object[]> findAllIdAndName();

    @Query("SELECT p.imageFileName FROM Product p "
            + "WHERE p.imageFileName IS NOT NULL AND p.imageFileName <> ''")
    List<String> findAllImageFileNames();
}
