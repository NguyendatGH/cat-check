package com.catcheck.shop.api;

import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import com.catcheck.shop.api.dto.AdminOrderResponse;
import com.catcheck.shop.api.dto.AdminOrderStatusRequest;
import com.catcheck.shop.application.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/shop/orders")
public class AdminOrderController {
    private final AdminOrderService service;
    public AdminOrderController(AdminOrderService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "adminListShopOrders", description = "20 đơn mỗi trang, `page` bắt đầu từ 0, `status` tuỳ chọn.")
    public List<AdminOrderResponse> list(@CurrentUser SecurityPrincipal user, @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page) {
        ShopAdminRole.require(user);
        return service.list(status, page).stream().map(AdminOrderResponse::from).toList();
    }

    @GetMapping("/{orderId}")
    @Operation(operationId = "adminGetShopOrder")
    public AdminOrderResponse get(@CurrentUser SecurityPrincipal user, @PathVariable UUID orderId) {
        ShopAdminRole.require(user);
        return AdminOrderResponse.from(service.get(orderId));
    }

    @PostMapping("/{orderId}/status")
    @Operation(operationId = "adminSetShopOrderStatus",
            description = "Chỉ chuyển tới trạng thái nằm trong `allowedNextStatuses` (sai ⇒ 409). `CANCELLED` bắt buộc `reason` ≥ 5 ký tự và trả lại kho.")
    public AdminOrderResponse status(@CurrentUser SecurityPrincipal user, @PathVariable UUID orderId, @Valid @RequestBody AdminOrderStatusRequest request) {
        String role = ShopAdminRole.require(user);
        return AdminOrderResponse.from(service.move(orderId, request.status(), request.reason(), user.userId(), role));
    }
}
