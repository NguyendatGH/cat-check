package com.catcheck.shop.domain;

public record CartLine(Product product, int quantity) {
    public long totalVnd() { return product.priceVnd() * quantity; }
}
