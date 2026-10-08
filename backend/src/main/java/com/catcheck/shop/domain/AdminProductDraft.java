package com.catcheck.shop.domain;

public record AdminProductDraft(String sku, String name, String description, String imageUrl,
                                long priceVnd, Long compareAtPriceVnd, int stockQuantity, String status) { }
