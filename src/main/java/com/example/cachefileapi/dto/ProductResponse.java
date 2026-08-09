package com.example.cachefileapi.dto;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Read-only response DTO returned from the API for a single
 * {@link com.example.cachefileapi.entity.Product}.
 *
 * <p>Implementing {@link Serializable} ensures this DTO can also be
 * cached directly in Redis when used as a cache value type.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ProductResponse implements Serializable {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
}
