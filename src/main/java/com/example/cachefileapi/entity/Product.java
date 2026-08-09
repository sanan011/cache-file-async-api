package com.example.cachefileapi.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Product entity — the base domain object used throughout caching demonstrations.
 *
 * <p>Implements {@link Serializable} so instances can be stored in Redis as-is
 * (or serialized via Jackson when using a JSON-based RedisSerializer).</p>
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Product implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotBlank(message = "Product name must not be blank")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(length = 1000)
    private String description;

    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Price format is invalid")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull(message = "Stock quantity must not be null")
    @Min(value = 0, message = "Stock quantity must be zero or greater")
    @Column(nullable = false)
    private Integer stockQuantity;
}
