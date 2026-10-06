package com.catcheck.scan.api;

import com.catcheck.scan.api.dto.AdminReassignCatRequest;
import com.catcheck.scan.api.dto.AdminScanListResponse;
import com.catcheck.scan.application.AdminActionContext;
import com.catcheck.scan.application.AdminScanService;
import com.catcheck.scan.application.ScanImageAccessService;
import com.catcheck.scan.application.ScanQueryService;
import com.catcheck.scan.domain.port.ScanQueryRepository;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tầng API của L5/L6/L18 — <b>nhánh phân quyền</b> (cột {@code R:} của p8 §8.4.12) và nhánh lỗi
 * chính. p14 §14.5.1 mục 2: UI ẩn nút với vai trò không có quyền, nhưng server vẫn phải kiểm độc
 * lập — đó là thứ được neo ở đây.
 *
 * <p>Ô Q5 của p14 gọi {@code DPO} là <i>"vai trò duy nhất"</i> xem được ảnh scan, nên test
 * khẳng định cả {@code ADMIN_SUPER} cũng bị chặn: đó là ô duy nhất trong toàn ma trận mà Super
 * <b>không</b> mạnh hơn Support.</p>
 */
class AdminScanControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final UUID SCAN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000d1");
    private static final UUID CAT_B = UUID.fromString("00000000-0000-7000-8000-0000000000c2");
    private static final String REASON = "Khách báo gán nhầm bé, ticket #771";

    private AdminScanService service;
    private ScanQueryService queryService;
    private AdminScanController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = mock(AdminScanService.class);
        queryService = mock(ScanQueryService.class);
        controller = new AdminScanController(service, queryService);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
    }

    /* ------------------------------------------------------------------ L5 */

    @Test
    @DisplayName("L5: ADMIN_CATALOG không đọc được số liệu scan ⇒ 403, service không chạy")
    void catalogCannotListScans() {
        assertThatThrownBy(() -> controller.listUserScans(
                principal(Set.of("ADMIN_CATALOG")), USER_ID, REASON, 0, 20, request))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.ACCESS_DENIED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L5: thiếu/ngắn reason ⇒ 400 REASON_REQUIRED trước khi gọi service")
    void shortReasonIsRejected() {
        assertThatThrownBy(() -> controller.listUserScans(
                principal(Set.of("ADMIN_SUPPORT")), USER_ID, "ngắn", 0, 20, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.REASON_REQUIRED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L5 (Support): KHÔNG có trường ảnh nào, tên mèo bị che, masked = true")
    void supportResponseHasNoImageFieldAndMaskedName() {
        when(service.listUserScans(eq(USER_ID), anyInt(), anyInt(), any(AdminActionContext.class)))
                .thenReturn(new AdminScanService.AdminScanPage(List.of(row()), 1, 0, 20, false));
        when(queryService.catName(CAT_B)).thenReturn("Luna");

        AdminScanListResponse response = controller.listUserScans(
                principal(Set.of("ADMIN_SUPPORT")), USER_ID, REASON, 0, 20, request);

        assertThat(response.masked()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().catName()).isEqualTo("L***");
        // p11 §11.5.4 dòng "Ảnh scan": Support = Không. Cờ "có ảnh" cũng không trả.
        assertThat(response.items().getFirst().imageStored()).isNull();
        assertThat(response.items().getFirst().disputedNote()).isEqualTo("g***");
        assertThat(response.items().getFirst().phValue()).isEqualByComparingTo("6.40");
    }

    @Test
    @DisplayName("L5 (DPO + DSAR mở): tên mèo và ghi chú đầy đủ, có cờ imageStored")
    void dpoWithDsarSeesFullRow() {
        when(service.listUserScans(eq(USER_ID), anyInt(), anyInt(), any(AdminActionContext.class)))
                .thenReturn(new AdminScanService.AdminScanPage(List.of(row()), 1, 0, 20, true));
        when(queryService.catName(CAT_B)).thenReturn("Luna");

        AdminScanListResponse response = controller.listUserScans(
                principal(Set.of("DPO")), USER_ID, REASON, 0, 20, request);

        assertThat(response.masked()).isFalse();
        assertThat(response.items().getFirst().catName()).isEqualTo("Luna");
        assertThat(response.items().getFirst().disputedNote()).isEqualTo("ghi chú tranh chấp");
        assertThat(response.items().getFirst().imageStored()).isTrue();
    }

    /* ------------------------------------------------------------------ L6 */

    @Test
    @DisplayName("L6: ADMIN_SUPER cũng KHÔNG xem được ảnh scan — DPO là vai trò duy nhất (p14 ô Q5)")
    void superAdminCannotViewScanImage() {
        assertThatThrownBy(() -> controller.getScanImage(
                principal(Set.of("ADMIN_SUPER", "ADMIN_SUPPORT")), SCAN_ID, REASON, request))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.ACCESS_DENIED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L6: DPO đi qua và response mang Cache-Control: no-store (quyền có thời hạn)")
    void dpoGetsImageWithNoStore() {
        when(service.openImageForDpo(eq(SCAN_ID), any(AdminActionContext.class)))
                .thenReturn(new ScanImageAccessService.Stream(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}), "image/jpeg", 3L));

        var response = controller.getScanImage(principal(Set.of("DPO")), SCAN_ID, REASON, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3L);
    }

    /* ----------------------------------------------------------------- L18 */

    @Test
    @DisplayName("L18: DPO không gán lại mèo được (p8 L18 chỉ Support/Super)")
    void dpoCannotReassign() {
        assertThatThrownBy(() -> controller.reassignScanCat(
                principal(Set.of("DPO")), SCAN_ID,
                new AdminReassignCatRequest(CAT_B.toString(), null, REASON), request))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L18: toAssignment = SHARED_UNKNOWN ⇒ toCatId bị bỏ qua, service nhận toShared = true")
    void sharedUnknownIgnoresCatId() {
        when(service.reassign(eq(SCAN_ID), eq(null), eq(true), any(AdminActionContext.class)))
                .thenReturn(new AdminScanService.AdminReassignResult(
                        SCAN_ID, CAT_B, null, "SHARED_UNKNOWN", (short) 1));

        var response = controller.reassignScanCat(
                principal(Set.of("ADMIN_SUPPORT")), SCAN_ID,
                new AdminReassignCatRequest(CAT_B.toString(), "SHARED_UNKNOWN", REASON), request);

        assertThat(response.toCatId()).isNull();
        assertThat(response.toAssignment()).isEqualTo("SHARED_UNKNOWN");
    }

    @Test
    @DisplayName("L18: toCatId không phải UUID ⇒ IllegalArgumentException (400), không phải 500")
    void malformedCatIdIsClientError() {
        assertThatThrownBy(() -> controller.reassignScanCat(
                principal(Set.of("ADMIN_SUPER")), SCAN_ID,
                new AdminReassignCatRequest("khong-phai-uuid", null, REASON), request))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    /* --------------------------------------------------------------- helper */

    private static ScanQueryRepository.Row row() {
        return new ScanQueryRepository.Row(
                SCAN_ID, CAT_B, USER_ID, "ASSIGNED", Instant.parse("2026-09-26T04:42:07Z"), "ANALYZED",
                new BigDecimal("6.40"), new BigDecimal("6.30"), new BigDecimal("6.50"),
                "NORMAL", null, new BigDecimal("0.82"), false,
                new BigDecimal("70.1"), new BigDecimal("2.3"), new BigDecimal("14.7"),
                88, "CHART_CARD", null, null, false, "engine-1.0.0",
                List.of(), true, false, true, null, null,
                null, "ghi chú tranh chấp", (short) 0, 120, "req-0");
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
