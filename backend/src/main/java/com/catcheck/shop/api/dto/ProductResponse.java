package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.Product;

public record ProductResponse(String id, String sku, String name, String description, String imageUrl,
                              long priceVnd, Long compareAtPriceVnd, int stockQuantity) {
    public static ProductResponse from(Product p) { return new ProductResponse(p.id().toString(), p.sku(), p.name(), p.description(), p.imageUrl(), p.priceVnd(), p.compareAtPriceVnd(), p.stockQuantity()); }
}
