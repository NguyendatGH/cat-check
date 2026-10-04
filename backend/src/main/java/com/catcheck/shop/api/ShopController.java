package com.catcheck.shop.api;

import com.catcheck.shop.api.dto.CartResponse;
import com.catcheck.shop.api.dto.CheckoutRequest;
import com.catcheck.shop.api.dto.OrderResponse;
import com.catcheck.shop.api.dto.ProductResponse;
import com.catcheck.shop.api.dto.SetCartLineRequest;
import com.catcheck.shop.application.ShopService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ShopController {
    private final ShopService service;
    public ShopController(ShopService service) { this.service = service; }

    @GetMapping("/shop/products")
    @Operation(operationId = "listShopProducts")
    public List<ProductResponse> products(@RequestParam(required = false) String query, @RequestParam(defaultValue = "50") int limit) {
        return service.products(query, limit).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/shop/products/{productId}")
    @Operation(operationId = "getShopProduct")
    public ProductResponse product(@PathVariable UUID productId) { return ProductResponse.from(service.product(productId)); }

    @GetMapping("/cart")
    @Operation(operationId = "getCart")
    public CartResponse cart(@CurrentUser SecurityPrincipal user) { return CartResponse.from(service.cart(user.userId())); }

    @PutMapping("/cart/{productId}")
    @Operation(operationId = "setCartLine")
    public CartResponse setLine(@CurrentUser SecurityPrincipal user, @PathVariable UUID productId, @Valid @RequestBody SetCartLineRequest request) {
        service.setLine(user.userId(), productId, request.quantity());
        return cart(user);
    }

    @DeleteMapping("/cart/{productId}")
    @Operation(operationId = "removeCartLine")
    public CartResponse removeLine(@CurrentUser SecurityPrincipal user, @PathVariable UUID productId) {
        service.removeLine(user.userId(), productId); return cart(user);
    }

    @DeleteMapping("/cart")
    @Operation(operationId = "clearCart")
    public ResponseEntity<Void> clearCart(@CurrentUser SecurityPrincipal user) { service.clearCart(user.userId()); return ResponseEntity.noContent().build(); }

    @PostMapping("/orders")
    @Operation(operationId = "createShopOrder")
    public ResponseEntity<OrderResponse> checkout(@CurrentUser SecurityPrincipal user, @Valid @RequestBody CheckoutRequest request) {
        OrderResponse response = OrderResponse.from(service.checkout(user.userId(), request.paymentMethod().toUpperCase(), request.receiverName(), request.receiverPhone(), request.shippingAddress()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(operationId = "getShopOrder")
    public OrderResponse order(@CurrentUser SecurityPrincipal user, @PathVariable UUID orderId) { return OrderResponse.from(service.order(user.userId(), orderId)); }
}
