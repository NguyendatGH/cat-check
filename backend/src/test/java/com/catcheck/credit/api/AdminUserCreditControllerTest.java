package com.catcheck.credit.api;

import com.catcheck.credit.api.dto.CreditAdjustmentRequest;
import com.catcheck.credit.application.AdminActionContext;
import com.catcheck.credit.application.AdminCreditService;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.AdminApiErrorCode;
import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tầng API của L9/L10 — <b>nhánh phân quyền</b> và <b>nhánh lỗi</b>, thứ p14 §14.5.1 mục 2 nói
 * server phải tự kiểm độc lập với UI.
 *
 * <p>Gọi thẳng method của controller chứ không qua {@code MockMvc}: điều cần neo là
 * {@code AdminGuard} chạy <b>trước</b> service (và service không bị gọi khi vai trò sai), cộng
 * với việc {@code reason} là ràng buộc API — hai thứ không cần dựng cả HTTP stack để chứng minh.
 * Phần bind/validate của body do bean validation lo và có test riêng ở tầng DTO.</p>
 */
class AdminUserCreditControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");

    private AdminCreditService service;
    private AdminUserCreditController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = mock(AdminCreditService.class);
        controller = new AdminUserCreditController(service);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Request-Id", "req-1");
    }

    @Test
    @DisplayName("L10: ADMIN_SUPPORT gọi thẳng endpoint vẫn nhận 403 ACCESS_DENIED (p14 §14.4.4 bước 1)")
    void supportCannotAdjustCredits() {
        assertThatThrownBy(() -> controller.adjust(
                principal(Set.of("ADMIN_SUPPORT")), USER_ID, grantRequest(), "key-1", request))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.ACCESS_DENIED);

        // Quan trọng hơn cả mã lỗi: service KHÔNG được chạm tới khi vai trò sai.
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L10: DPO cũng không điều chỉnh được credit (p11 §11.5.4 — DPO chỉ Đọc)")
    void dpoCannotAdjustCredits() {
        assertThatThrownBy(() -> controller.adjust(
                principal(Set.of("DPO")), USER_ID, grantRequest(), null, request))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L10: ADMIN_SUPER đi qua, Idempotency-Key được chuyển xuống service nguyên vẹn")
    void superAdminAdjustsAndKeyIsForwarded() {
        when(service.adjust(eq(USER_ID), eq(AdminCreditService.AdjustmentDirection.GRANT), eq(25),
                eq("PLUS"), eq(14), eq("key-1"), any()))
                .thenReturn(new AdminCreditService.AdminCreditAdjustmentResult(
                        AdminCreditService.AdjustmentDirection.GRANT, 25, 0, 25,
                        UUID.fromString("00000000-0000-7000-8000-0000000000c1"),
                        Instant.parse("2026-10-20T09:00:00Z"), List.of(), false));

        var response = controller.adjust(
                principal(Set.of("ADMIN_SUPER")), USER_ID, grantRequest(), "key-1", request);

        assertThat(response.direction()).isEqualTo("GRANT");
        assertThat(response.balanceAfter()).isEqualTo(25);
        assertThat(response.replayed()).isFalse();
    }

    @Test
    @DisplayName("L10: reason dưới 10 ký tự ⇒ 400 REASON_REQUIRED ở TẦNG API, service không chạy")
    void shortReasonIsRejectedAtApiLayer() {
        CreditAdjustmentRequest tooShort =
                new CreditAdjustmentRequest("GRANT", 10, "PLUS", null, "ngắn");

        assertThatThrownBy(() -> controller.adjust(
                principal(Set.of("ADMIN_SUPER")), USER_ID, tooShort, null, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.REASON_REQUIRED);

        verify(service, never()).adjust(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("L9: ADMIN_SUPPORT đọc được, và response mang Cache-Control: no-store")
    void supportCanReadCreditsWithNoStore() {
        when(service.overview(eq(USER_ID), anyInt(), anyInt(), any(AdminActionContext.class)))
                .thenReturn(new AdminCreditService.AdminCreditOverview(
                        7, List.of(), List.of(), 0L, 0, 20));

        var response = controller.credits(
                principal(Set.of("ADMIN_SUPPORT")), USER_ID, 0, 20, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().availableBalance()).isEqualTo(7);
    }

    @Test
    @DisplayName("L9: vai trò ngoài ba vai trò được phép (ADMIN_CATALOG) ⇒ 403 ACCESS_DENIED")
    void catalogCannotReadCredits() {
        assertThatThrownBy(() -> controller.credits(
                principal(Set.of("ADMIN_CATALOG")), USER_ID, 0, 20, request))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(service);
    }

    /* --------------------------------------------------------------- helper */

    private static CreditAdjustmentRequest grantRequest() {
        return new CreditAdjustmentRequest(
                "GRANT", 25, "PLUS", 14, "Bồi hoàn do lỗi hệ thống, ticket #482");
    }

    private static SecurityPrincipal principal(Set<String> roles) {
        return new SecurityPrincipal() {
            @Override
            public UUID userId() {
                return ADMIN_ID;
            }

            @Override
            public String email() {
                return "admin@catcheck.vn";
            }

            @Override
            public Set<String> roles() {
                return roles;
            }

            @Override
            public MfaLevel mfaLevel() {
                return MfaLevel.TOTP;
            }
        };
    }
}
