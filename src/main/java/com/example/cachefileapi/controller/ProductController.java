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

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Products", description = "Product CRUD and image upload/download")
public class ProductController {

    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    // -----------------------------------------------------------------------
    // CRUD endpoints
    // -----------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List all products", description = "Returns every product in the catalog.")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns a single product by its primary key.")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

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

    @PutMapping("/{id}")
    @Operation(summary = "Update a product", description = "Replaces an existing product's fields.")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "Product ID") @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

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

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace product image", description = "Accepts multipart/form-data with a single 'file' field. "
            + "Validated for size (≤5 MB), extension, content type, and magic bytes.")
    public ResponseEntity<Map<String, String>> uploadImage(
            @Parameter(description = "Product ID") @PathVariable Long id,
            @Parameter(description = "Image file to upload (max 5 MB; allowed: jpg, jpeg, png, gif, webp)", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(type = "string", format = "binary"))) @RequestPart("file") MultipartFile file) {

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

    @GetMapping("/{id}/image")
    @Operation(summary = "Download product image", description = "Returns the raw image bytes for the product. Responds with 404 if no image is set.")
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
