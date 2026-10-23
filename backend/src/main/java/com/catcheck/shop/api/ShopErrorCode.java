package com.catcheck.shop.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ShopErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("shop/product-not-found", HttpStatus.NOT_FOUND),
    CART_EMPTY("shop/cart-empty", HttpStatus.CONFLICT),
    ORDER_NOT_FOUND("shop/order-not-found", HttpStatus.NOT_FOUND),
    STOCK_UNAVAILABLE("shop/stock-unavailable", HttpStatus.CONFLICT),
    ORDER_LIMIT_REACHED("shop/order-limit-reached", HttpStatus.CONFLICT),
    ORDER_NOT_CANCELLABLE("shop/order-not-cancellable", HttpStatus.CONFLICT),
    ORDER_TRANSITION_INVALID("shop/order-transition-invalid", HttpStatus.CONFLICT),
    PAYMENT_UNAVAILABLE("shop/payment-unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    PRODUCT_SKU_DUPLICATE("shop/product-sku-duplicate", HttpStatus.CONFLICT),
    PRODUCT_INVALID("shop/product-invalid", HttpStatus.BAD_REQUEST),
    ADMIN_ROLE_REQUIRED("shop/admin-role-required", HttpStatus.FORBIDDEN),
    PROMO_CODE_INVALID("shop/promo-code-invalid", HttpStatus.BAD_REQUEST),
    PROMO_CODE_DUPLICATE("shop/promo-code-duplicate", HttpStatus.CONFLICT),
    PROMO_CODE_NOT_FOUND("shop/promo-code-not-found", HttpStatus.NOT_FOUND);

    private final String slug;
    private final HttpStatus status;
    ShopErrorCode(String slug, HttpStatus status) { this.slug = slug; this.status = status; }
    @Override public String code() { return name(); }
    @Override public HttpStatus status() { return status; }
    @Override public URI typeUri() { return ProblemTypes.of(slug); }
}
