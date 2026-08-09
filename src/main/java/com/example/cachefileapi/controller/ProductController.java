package com.example.cachefileapi.controller;

import com.example.cachefileapi.config.CacheConstants;
import com.example.cachefileapi.dto.ProductRequest;
import com.example.cachefileapi.dto.ProductResponse;
import com.example.cachefileapi.entity.Product;
import com.example.cachefileapi.exception.ResourceNotFoundException;
import com.example.cachefileapi.repository.ProductRepository;
import com.example.cachefileapi.service.FileStorageService;
import com.example.cachefileapi.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

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
@Slf4j
@Tag(name = "Products", description = "Product CRUD and image upload/download")
public class ProductController {

    private final ProductService     productService;
    private final FileStorageService fileStorageService;
    private final ProductRepository  productRepository;
    private final CacheManager       cacheManager;

    // -----------------------------------------------------------------------
    // CRUD endpoints
    // -----------------------------------------------------------------------

    /**
     * {@code GET /products} — list all products.
     *
     * @return 200 OK with the list of all products
     */
    @GetMapping
    @Operation(summary = "List all products", description = "Returns every product in the catalog.")
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
    @Operation(summary = "Get product by ID", description = "Returns a single product by its primary key.")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * {@code POST /products} — create a new product.
     *
     * @param request validated product data
     * @return 201 Created with a {@code Location} header and the created product body
     */
    @PostMapping
    @Operation(summary = "Create a product", description = "Creates a new product and triggers an async notification.")
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
    @Operation(summary = "Update a product", description = "Replaces an existing product's fields.")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "Product ID") @PathVariable Long id,
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
    @Operation(summary = "Delete a product", description = "Permanently removes a product by ID.")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Image upload / download endpoints
    // -----------------------------------------------------------------------

    /**
     * {@code POST /products/{id}/image} — upload or replace the product image.
     *
     * <p>Accepts {@code multipart/form-data} with a single field named {@code file}.
     * The file is validated (not empty, ≤5 MB, allowed extension, {@code image/*}
     * content type) and stored under a UUID-based filename. The product's
     * {@code imageFileName} field is updated and the relevant caches are evicted.</p>
     *
     * @param id   the product primary key
     * @param file the image file to upload
     * @return 200 OK with {@code {"imageFileName": "<generated-name>"}}
     */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace product image",
            description = "Accepts multipart/form-data with a single 'file' field. "
                    + "Validated for size (≤5 MB), extension, content type, and magic bytes.")
    public ResponseEntity<Map<String, String>> uploadImage(
            @Parameter(description = "Product ID") @PathVariable Long id,
            @Parameter(
                    description = "Image file to upload (max 5 MB; allowed: jpg, jpeg, png, gif, webp)",
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        String fileName = fileStorageService.storeFile(file, id);

        product.setImageFileName(fileName);
        productRepository.save(product);

        // Evict caches so stale entries don't hide the updated product
        evictProductCaches(id);

        log.info("Image uploaded for product id={}: fileName='{}'", id, fileName);
        return ResponseEntity.ok(Map.of("imageFileName", fileName));
    }

    /**
     * {@code GET /products/{id}/image} — download the product image.
     *
     * <p>Returns the raw image bytes with the correct {@code Content-Type} header.
     * Responds with {@code 404 Not Found} if the product has no image set or the
     * file cannot be found on disk.</p>
     *
     * @param id the product primary key
     * @return 200 OK with image bytes, or 404 if no image is set
     */
    @GetMapping("/{id}/image")
    @Operation(
            summary = "Download product image",
            description = "Returns the raw image bytes for the product. Responds with 404 if no image is set.")
    public ResponseEntity<Resource> downloadImage(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        String imageFileName = product.getImageFileName();
        if (imageFileName == null || imageFileName.isBlank()) {
            throw new ResourceNotFoundException(
                    "Product with id " + id + " has no image uploaded.");
        }

        Resource resource = fileStorageService.loadFileAsResource(imageFileName);

        // Probe the stored file's content type; fall back to octet-stream if unknown
        String contentType;
        try {
            contentType = Files.probeContentType(resource.getFile().toPath());
        } catch (Exception e) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Manually evicts the per-product and list caches after an image update.
     *
     * <p>We cannot use {@code @CacheEvict} here because Spring's proxy-based AOP
     * only intercepts {@link ProductService} beans, not controller methods.</p>
     */
    private void evictProductCaches(Long id) {
        var productsCache = cacheManager.getCache(CacheConstants.PRODUCTS);
        if (productsCache != null) {
            productsCache.evictIfPresent(id);
        }
        var listCache = cacheManager.getCache(CacheConstants.PRODUCTS_LIST);
        if (listCache != null) {
            listCache.evictIfPresent("all");
        }
    }
}
