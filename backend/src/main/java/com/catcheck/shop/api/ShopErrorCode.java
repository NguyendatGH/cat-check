package com.catcheck.shop.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ShopErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("shop/product-not-found", HttpStatus.NOT_FOUND),
    CART_EMPTY("shop/cart-empty", HttpStatus.CONFLICT),
    ORDER_NOT_FOUND("shop/order-not-found", HttpStatus.NOT_FOUND),
    STOCK_UNAVAILABLE("shop/stock-unavailable", HttpStatus.CONFLICT),
    PRODUCT_SKU_DUPLICATE("shop/product-sku-duplicate", HttpStatus.CONFLICT),
    PRODUCT_INVALID("shop/product-invalid", HttpStatus.BAD_REQUEST),
    ADMIN_ROLE_REQUIRED("shop/admin-role-required", HttpStatus.FORBIDDEN);

    private final String slug;
    private final HttpStatus status;
    ShopErrorCode(String slug, HttpStatus status) { this.slug = slug; this.status = status; }
    @Override public String code() { return name(); }
    @Override public HttpStatus status() { return status; }
    @Override public URI typeUri() { return URI.create("https://catcheck.vn/problems/" + slug); }
}
