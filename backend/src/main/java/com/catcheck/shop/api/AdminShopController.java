package com.catcheck.shop.api;

import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import com.catcheck.shop.api.dto.AdminProductRequest;
import com.catcheck.shop.api.dto.AdminProductResponse;
import com.catcheck.shop.api.dto.AdminProductStatusRequest;
import com.catcheck.shop.application.AdminShopService;
import com.catcheck.shop.domain.AdminProduct;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/shop/products")
public class AdminShopController {
    private static final Set<String> ROLES = Set.of("ADMIN_SUPER", "ADMIN_CATALOG");

    private final AdminShopService service;
    public AdminShopController(AdminShopService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "adminListShopProducts")
    public List<AdminProductResponse> list(@CurrentUser SecurityPrincipal user) {
        role(user);
        return service.list().stream().map(AdminShopController::response).toList();
    }

    @PostMapping
    @Operation(operationId = "adminCreateShopProduct")
    public ResponseEntity<AdminProductResponse> create(@CurrentUser SecurityPrincipal user, @Valid @RequestBody AdminProductRequest request) {
        String role = role(user);
        AdminProductResponse r = response(service.create(request, user.userId(), role));
        return ResponseEntity.created(URI.create("/api/v1/admin/shop/products/" + r.id())).eTag(r.etag()).body(r);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "adminUpdateShopProduct",
            description = "`If-Match` bắt buộc = `etag` của bản đang sửa, dạng `W/\"<updatedAt epoch ms>-<id>\"` "
                    + "(thiếu ⇒ 428 PRECONDITION_REQUIRED, lệch ⇒ 412 RESOURCE_MODIFIED): PUT ghi đè tồn kho.")
    public ResponseEntity<AdminProductResponse> update(@CurrentUser SecurityPrincipal user, @PathVariable UUID id,
                                                       @RequestHeader(name = "If-Match", required = false) String ifMatch,
                                                       @Valid @RequestBody AdminProductRequest request) {
        String role = role(user);
        AdminProductResponse r = response(service.update(id, request, ifMatch, user.userId(), role));
        return ResponseEntity.ok().eTag(r.etag()).body(r);
    }

    @PatchMapping("/{id}/status")
    @Operation(operationId = "adminSetShopProductStatus")
    public AdminProductResponse status(@CurrentUser SecurityPrincipal user, @PathVariable UUID id, @Valid @RequestBody AdminProductStatusRequest request) {
        String role = role(user);
        return response(service.setStatus(id, request.status(), user.userId(), role));
    }

    private static AdminProductResponse response(AdminProduct p) {
        return AdminProductResponse.from(p, AdminShopService.etagOf(p));
    }

    private static String role(SecurityPrincipal user) {
        if (user != null) {
            for (String r : user.roles()) {
                String n = r.startsWith("ROLE_") ? r.substring(5) : r;
                if (ROLES.contains(n)) return n;
            }
        }
        throw new PermissionDeniedException(ShopErrorCode.ADMIN_ROLE_REQUIRED, String.join(",", ROLES));
    }
}
