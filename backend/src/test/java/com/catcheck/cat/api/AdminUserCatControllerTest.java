package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.AdminCatListResponse;
import com.catcheck.cat.application.AdminActionContext;
import com.catcheck.cat.application.AdminCatViewService;
import com.catcheck.cat.domain.Cat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tầng API của L4 — nhánh phân quyền (cột {@code R:} của p8 L4) và nhánh lỗi {@code reason}, cộng
 * với hai tính chất của response mà p8/p15 ràng buộc tường minh: <b>không trả
 * {@code storage_key}</b> (p8 §8.2.5 mục 3) và <b>che văn bản tự do</b> (p11 §11.5.4).
 */
class AdminUserCatControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-10-06T09:00:00Z");
    private static final String REASON = "Khách báo sai tên bé, ticket #902";

    private AdminCatViewService service;
    private AdminUserCatController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = mock(AdminCatViewService.class);
        controller = new AdminUserCatController(service);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
    }

    @Test
    @DisplayName("L4: ADMIN_CATALOG không đọc được hồ sơ mèo (p14 ô Q3 = ❌) ⇒ 403, service không chạy")
    void catalogCannotListCats() {
        assertThatThrownBy(() -> controller.listCats(
                principal(Set.of("ADMIN_CATALOG")), USER_ID, REASON, request))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.ACCESS_DENIED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L4: reason dưới 10 ký tự ⇒ 400 REASON_REQUIRED ở TẦNG API (p15 REQ-AUD-03)")
    void shortReasonIsRejectedAtApiLayer() {
        assertThatThrownBy(() -> controller.listCats(
                principal(Set.of("ADMIN_SUPPORT")), USER_ID, "ngắn", request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.REASON_REQUIRED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("L4 (Support): tên/ghi chú bị che, có hasAvatar nhưng KHÔNG có storage_key, no-store")
    void supportSeesMaskedProfileWithoutStorageKey() {
        when(service.listCatsOf(eq(USER_ID), any(AdminActionContext.class)))
                .thenReturn(new AdminCatViewService.AdminCatListing(List.of(catWithAvatar()), false));

        var response = controller.listCats(principal(Set.of("ADMIN_SUPPORT")), USER_ID, REASON, request);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        AdminCatListResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.masked()).isTrue();
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().getFirst().name()).isEqualTo("L***");
        assertThat(body.items().getFirst().notes()).isEqualTo("N***");
        assertThat(body.items().getFirst().hasAvatar()).isTrue();
        // Mã hiển thị KHÔNG bị che: đó là mã người dùng đọc qua điện thoại để xác nhận đúng bé.
        assertThat(body.items().getFirst().publicCode()).isEqualTo("CC-VN-A1B2C3");
        // p8 §8.2.5 mục 3: `storage_key` không bao giờ rời khỏi server. Neo bằng toàn bộ nội dung
        // serialise được của DTO, không chỉ bằng việc "DTO hiện không có field đó" — thêm một field
        // mang khoá lưu trữ về sau sẽ làm test này đỏ.
        assertThat(body.items().getFirst().toString()).doesNotContain("cat-avatar/");
    }

    @Test
    @DisplayName("L4 (DPO + DSAR mở): trả nguyên văn tên và ghi chú, masked = false")
    void dpoWithDsarSeesFullProfile() {
        when(service.listCatsOf(eq(USER_ID), any(AdminActionContext.class)))
                .thenReturn(new AdminCatViewService.AdminCatListing(List.of(catWithAvatar()), true));

        var response = controller.listCats(principal(Set.of("DPO")), USER_ID, REASON, request);

        AdminCatListResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.masked()).isFalse();
        assertThat(body.items().getFirst().name()).isEqualTo("Luna");
        assertThat(body.items().getFirst().notes()).isEqualTo("Nhạy cảm với cát bụi");
    }

    @Test
    @DisplayName("L4: danh sách rỗng trả items rỗng + hasMore = false, KHÔNG trả 404 (p8 §8.1.4)")
    void emptyListingIsNotANotFound() {
        when(service.listCatsOf(eq(USER_ID), any(AdminActionContext.class)))
                .thenReturn(new AdminCatViewService.AdminCatListing(List.of(), false));

        var response = controller.listCats(principal(Set.of("ADMIN_SUPER")), USER_ID, REASON, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().page().hasMore()).isFalse();
    }

    /* --------------------------------------------------------------- helper */

    private static Cat catWithAvatar() {
        Cat cat = Cat.create(UUID.fromString("00000000-0000-7000-8000-0000000000c1"), USER_ID,
                "Luna", LocalDate.of(2023, 5, 1), null, "CC-VN-A1B2C3",
                LocalDate.of(2026, 10, 6), NOW);
        cat.applyProfile("PERSIAN", null, "Trắng", null, null, null,
                "Nhạy cảm với cát bụi", LocalDate.of(2026, 10, 6), NOW);
        cat.attachAvatar("cat-avatar/abc.jpg",
                com.catcheck.cat.domain.AvatarStorageProvider.LOCAL, NOW);
        return cat;
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
