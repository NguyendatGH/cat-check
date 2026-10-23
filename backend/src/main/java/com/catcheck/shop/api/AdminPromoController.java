package com.catcheck.shop.api;

import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import com.catcheck.shop.api.dto.AdminPromoCodeRequest;
import com.catcheck.shop.api.dto.AdminPromoCodeResponse;
import com.catcheck.shop.api.dto.AdminPromoCodeStatusRequest;
import com.catcheck.shop.application.AdminPromoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/shop/promos")
public class AdminPromoController {
    private final AdminPromoService service;

    public AdminPromoController(AdminPromoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "adminListShopPromos")
    public List<AdminPromoCodeResponse> list(@CurrentUser SecurityPrincipal user) {
        ShopAdminRole.require(user);
        return service.list().stream().map(AdminPromoCodeResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "adminGetShopPromo")
    public AdminPromoCodeResponse get(@CurrentUser SecurityPrincipal user, @PathVariable UUID id) {
        ShopAdminRole.require(user);
        return AdminPromoCodeResponse.from(service.get(id));
    }

    @PostMapping
    @Operation(operationId = "adminCreateShopPromo")
    public ResponseEntity<AdminPromoCodeResponse> create(@CurrentUser SecurityPrincipal user, @Valid @RequestBody AdminPromoCodeRequest request) {
        String role = ShopAdminRole.require(user);
        AdminPromoCodeResponse response = AdminPromoCodeResponse.from(service.create(request, user.userId(), role));
        return ResponseEntity.created(URI.create("/api/v1/admin/shop/promos/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "adminUpdateShopPromo")
    public AdminPromoCodeResponse update(@CurrentUser SecurityPrincipal user, @PathVariable UUID id, @Valid @RequestBody AdminPromoCodeRequest request) {
        String role = ShopAdminRole.require(user);
        return AdminPromoCodeResponse.from(service.update(id, request, user.userId(), role));
    }

    @PatchMapping("/{id}/status")
    @Operation(operationId = "adminSetShopPromoStatus")
    public AdminPromoCodeResponse setStatus(@CurrentUser SecurityPrincipal user, @PathVariable UUID id, @Valid @RequestBody AdminPromoCodeStatusRequest request) {
        String role = ShopAdminRole.require(user);
        return AdminPromoCodeResponse.from(service.setStatus(id, request.isActive(), user.userId(), role));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "adminDeleteShopPromo")
    public ResponseEntity<Void> delete(@CurrentUser SecurityPrincipal user, @PathVariable UUID id) {
        String role = ShopAdminRole.require(user);
        service.delete(id, user.userId(), role);
        return ResponseEntity.noContent().build();
    }
}
