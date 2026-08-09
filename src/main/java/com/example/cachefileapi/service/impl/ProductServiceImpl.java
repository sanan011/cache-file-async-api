package com.example.cachefileapi.service.impl;

import com.example.cachefileapi.config.CacheConstants;
import com.example.cachefileapi.dto.ProductRequest;
import com.example.cachefileapi.dto.ProductResponse;
import com.example.cachefileapi.entity.Product;
import com.example.cachefileapi.exception.DuplicateResourceException;
import com.example.cachefileapi.exception.ResourceNotFoundException;
import com.example.cachefileapi.repository.ProductRepository;
import com.example.cachefileapi.service.NotificationService;
import com.example.cachefileapi.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default implementation of {@link ProductService}.
 *
 * <h3>Caching strategy</h3>
 * <ul>
 *   <li>{@code getAllProducts} — cached in {@link CacheConstants#PRODUCTS_LIST}.
 *       Evicted on any write operation.</li>
 *   <li>{@code getProductById} — cached per-id in {@link CacheConstants#PRODUCTS}.</li>
 *   <li>{@code createProduct} — evicts the list cache after creation and fires an async notification.</li>
 *   <li>{@code updateProduct} — updates the per-id cache entry and evicts the list cache.</li>
 *   <li>{@code deleteProduct} — evicts both the per-id entry and the list cache.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    // -----------------------------------------------------------------------
    // Read operations
    // -----------------------------------------------------------------------

    @Override
    @Cacheable(value = CacheConstants.PRODUCTS_LIST, key = "'all'")
    public List<ProductResponse> getAllProducts() {
        log.debug("Fetching all products from database");
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConstants.PRODUCTS, key = "#id")
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product with id={} from database", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toResponse(product);
    }

    // -----------------------------------------------------------------------
    // Write operations
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    @CacheEvict(value = CacheConstants.PRODUCTS_LIST, allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException(
                    "Product with name '" + request.getName() + "' already exists");
        }
        Product product = toEntity(request);
        Product saved = productRepository.save(product);
        log.info("Created product id={}, name='{}'", saved.getId(), saved.getName());
        notificationService.sendProductCreatedNotification(saved.getName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(
        put    = @CachePut(value  = CacheConstants.PRODUCTS, key = "#id"),
        evict  = @CacheEvict(value = CacheConstants.PRODUCTS_LIST, allEntries = true)
    )
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());

        Product saved = productRepository.save(existing);
        log.info("Updated product id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConstants.PRODUCTS,      key = "#id"),
        @CacheEvict(value = CacheConstants.PRODUCTS_LIST, allEntries = true)
    })
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product id={}", id);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .build();
    }

    private Product toEntity(ProductRequest request) {
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .build();
    }
}
