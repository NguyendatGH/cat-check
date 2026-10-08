package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.AdminProduct;

import java.time.Instant;

public record AdminProductResponse(String id, String sku, String name, String description, String imageUrl,
                                   long priceVnd, Long compareAtPriceVnd, int stockQuantity, String status, Instant updatedAt) {
    public static AdminProductResponse from(AdminProduct p) {
        return new AdminProductResponse(p.id().toString(), p.sku(), p.name(), p.description(), p.imageUrl(),
                p.priceVnd(), p.compareAtPriceVnd(), p.stockQuantity(), p.status(), p.updatedAt());
    }
}
