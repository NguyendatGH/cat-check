package com.catcheck.shop.domain;

import java.time.Instant;
import java.util.UUID;

public record AdminProduct(UUID id, String sku, String name, String description, String imageUrl,
                           long priceVnd, Long compareAtPriceVnd, int stockQuantity, String status, Instant updatedAt) { }
