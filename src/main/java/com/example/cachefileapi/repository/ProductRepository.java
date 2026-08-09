package com.example.cachefileapi.repository;

import com.example.cachefileapi.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Product}.
 *
 * <p>Extends {@link JpaRepository} which provides standard CRUD operations
 * (save, findById, findAll, delete, etc.) out of the box. Custom query methods
 * are defined below as named query derivations or {@code @Query} annotations.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Returns all products whose name contains the given keyword (case-insensitive).
     *
     * @param keyword substring to search for in the product name
     * @return matching products, empty list if none found
     */
    List<Product> findByNameContainingIgnoreCase(String keyword);

    /**
     * Returns all products with a price within the specified range (inclusive).
     *
     * @param minPrice minimum price (inclusive)
     * @param maxPrice maximum price (inclusive)
     * @return products in the price range
     */
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * Returns all products that have at least {@code minStock} units in stock.
     *
     * @param minStock minimum stock quantity threshold
     * @return in-stock products above the threshold
     */
    List<Product> findByStockQuantityGreaterThanEqual(Integer minStock);

    /**
     * Checks whether a product with the given name already exists (used for duplicate detection).
     *
     * @param name product name to check
     * @return {@code true} if a product with this name exists
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Returns a lightweight projection of all products (id + name only) for list views
     * where the full entity is not needed.
     */
    @Query("SELECT p.id AS id, p.name AS name FROM Product p ORDER BY p.name ASC")
    List<Object[]> findAllIdAndName();
}
