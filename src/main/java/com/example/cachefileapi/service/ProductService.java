package com.example.cachefileapi.service;

import com.example.cachefileapi.dto.ProductRequest;
import com.example.cachefileapi.dto.ProductResponse;

import java.util.List;

/**
 * Service contract for {@link com.example.cachefileapi.entity.Product} operations.
 *
 * <p>Defines the public API that controllers and other services depend on,
 * keeping callers decoupled from the implementation (and any caching strategy).</p>
 */
public interface ProductService {

    /**
     * Retrieves all products.
     *
     * @return list of all products as response DTOs
     */
    List<ProductResponse> getAllProducts();

    /**
     * Retrieves a single product by its primary key.
     *
     * @param id product identifier
     * @return the product response DTO
     * @throws com.example.cachefileapi.exception.ResourceNotFoundException if not found
     */
    ProductResponse getProductById(Long id);

    /**
     * Creates a new product.
     *
     * @param request product data
     * @return the created product as a response DTO
     * @throws com.example.cachefileapi.exception.DuplicateResourceException if name already exists
     */
    ProductResponse createProduct(ProductRequest request);

    /**
     * Updates an existing product.
     *
     * @param id      product identifier
     * @param request updated product data
     * @return the updated product as a response DTO
     * @throws com.example.cachefileapi.exception.ResourceNotFoundException if not found
     */
    ProductResponse updateProduct(Long id, ProductRequest request);

    /**
     * Deletes a product by its primary key.
     *
     * @param id product identifier
     * @throws com.example.cachefileapi.exception.ResourceNotFoundException if not found
     */
    void deleteProduct(Long id);
}
