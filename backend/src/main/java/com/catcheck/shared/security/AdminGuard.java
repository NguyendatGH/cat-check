package com.catcheck.shared.security;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;

import java.util.Set;

/**
 * Hai điều kiện mà <b>mỗi</b> endpoint admin phải tự kiểm ở tầng {@code ..api..}: vai trò
 * (cột {@code R:} của p8 §8.4.12) và lý do (ký hiệu {@code Rsn}).
 *
 * <p><b>Vì sao kiểm lại ở controller dù chuỗi filter đã chặn {@code /api/v1/admin/**}:</b>
 * filter chỉ biết "có phải nhóm role admin không", còn p8 cấp quyền tới từng endpoint — ví dụ
 * L19 cho {@code ADMIN_SUPER,ADMIN_SUPPORT} nhưng L20 chỉ cho {@code ADMIN_SUPER}. p14 §14.5.1
 * mục 2 nói thẳng: UI ẩn nút với vai trò không có quyền, nhưng <b>server vẫn phải kiểm độc
 * lập</b>.</p>
 *
 * <p><b>Hai dạng tên vai trò:</b> {@code user_role.role} không có tiền tố {@code ROLE_}
 * (p4 §4.4.3) và {@link SecurityPrincipal#roles()} giữ nguyên dạng đó, trong khi Spring
 * Security sinh {@code GrantedAuthority} có tiền tố. Lớp này chấp nhận cả hai để nơi gọi không
 * phải nhớ đang ở phía nào — giống {@code ColorChartRoleGuard}/{@code ContentRoleGuard}.</p>
 */
public final class AdminGuard {

    /** p15 REQ-AUD-03 / p14 §14.3.2 mục 3: {@code reason} tối thiểu 10 ký tự, không kể khoảng trắng hai đầu. */
    public static final int REASON_MIN_LENGTH = 10;

    private static final String ROLE_PREFIX = "ROLE_";

    private AdminGuard() {
    }

    public static boolean hasAnyRole(SecurityPrincipal principal, Set<String> allowed) {
        if (principal == null) {
            return false;
        }
        return principal.roles().stream().anyMatch(role ->
                allowed.contains(role) || allowed.contains(ROLE_PREFIX + role)
                        || (role.startsWith(ROLE_PREFIX) && allowed.contains(role.substring(ROLE_PREFIX.length()))));
    }

    /**
     * @throws PermissionDeniedException {@code 403 ACCESS_DENIED} nếu không có vai trò nào trong
     *                                   {@code allowed}
     */
    public static void requireAnyRole(SecurityPrincipal principal, Set<String> allowed) {
        if (!hasAnyRole(principal, allowed)) {
            // Thông điệp liệt kê vai trò CẦN, không liệt kê vai trò người gọi ĐANG có: đó là
            // thông tin về chính họ nên không lộ gì, còn danh sách cần thì giúp người trực tổng
            // đài biết phải chuyển việc cho ai.
            throw new PermissionDeniedException(AdminApiErrorCode.ACCESS_DENIED,
                    String.join(", ", allowed.stream().sorted().toList()));
        }
    }

    /**
     * Chuẩn hoá và bắt buộc {@code reason}.
     *
     * @return {@code reason} đã {@code strip()} — giá trị đem ghi {@code audit_log.metadata}
     * @throws BusinessRuleException {@code 400 REASON_REQUIRED} nếu thiếu, rỗng, hoặc ngắn hơn
     *                               {@value #REASON_MIN_LENGTH} ký tự
     */
    public static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < REASON_MIN_LENGTH) {
            throw new BusinessRuleException(AdminApiErrorCode.REASON_REQUIRED, REASON_MIN_LENGTH);
        }
        return trimmed;
    }
}
