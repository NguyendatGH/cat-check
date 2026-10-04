package com.catcheck.shop.domain;

import java.util.UUID;

public record Product(UUID id, String sku, String name, String description, String imageUrl,
                      long priceVnd, Long compareAtPriceVnd, int stockQuantity) { }
