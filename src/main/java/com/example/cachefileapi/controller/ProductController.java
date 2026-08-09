package com.example.cachefileapi.controller;

import com.example.cachefileapi.dto.ProductRequest;
import com.example.cachefileapi.dto.ProductResponse;
import com.example.cachefileapi.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST controller exposing CRUD endpoints for {@link com.example.cachefileapi.entity.Product}.
 *
 * <p>Base path: {@code /api/products} (context path {@code /api} is set in
 * {@code application.yml}, this controller maps {@code /products}).</p>
 *
 * <p>All request bodies are validated via {@link Valid} — validation errors
 * are handled centrally by {@link com.example.cachefileapi.exception.GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * {@code GET /products} — list all products.
     *
     * @return 200 OK with the list of all products
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    /**
     * {@code GET /products/{id}} — get a single product by id.
     *
     * @param id the product primary key
     * @return 200 OK with the product, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * {@code POST /products} — create a new product.
     *
     * @param request validated product data
     * @return 201 Created with a {@code Location} header and the created product body
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * {@code PUT /products/{id}} — replace an existing product.
     *
     * @param id      the product primary key
     * @param request validated updated product data
     * @return 200 OK with the updated product, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    /**
     * {@code DELETE /products/{id}} — delete a product.
     *
     * @param id the product primary key
     * @return 204 No Content, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
