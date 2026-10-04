package com.catcheck.identity.infrastructure.security;

import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.AdminAccessGate;
import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Locale;

/**
 * Điều kiện chung #1 của p8 §8.4.12 cho mọi endpoint {@code /api/v1/admin/**}: phiên phải có
 * {@code mfaLevel = TOTP}.
 *
 * <ul>
 *   <li>Tài khoản <b>đã đăng ký</b> TOTP nhưng phiên chưa nhập mã ⇒ {@code 403 ADMIN_TOTP_REQUIRED}.</li>
 *   <li>Tài khoản <b>chưa đăng ký</b> TOTP (không có dòng {@code user_mfa_totp} hoặc còn
 *       {@code PENDING}) ⇒ {@code 403 TOTP_SETUP_REQUIRED} — FE điều hướng cứng sang
 *       {@code /admin/setup-2fa} (p14 §14.5.3).</li>
 * </ul>
 *
 * <p><b>Vì sao đặt SAU {@code AuthorizationFilter}</b> (xem {@code SecurityConfig}): thứ tự kiểm
 * của p8 §8.3.1 bước 4a là vai trò trước, MFA sau. Nếu gác chạy trước thì một user thường gõ
 * {@code /api/v1/admin/users} sẽ nhận {@code TOTP_SETUP_REQUIRED} — vừa sai ngữ nghĩa, vừa tiết
 * lộ rằng khu vực admin tồn tại và chỉ còn thiếu 2FA. Chạy sau thì họ nhận {@code 403} của
 * Spring Security như mọi tài nguyên bị cấm khác.</p>
 *
 * <p>Vì chạy sau {@code AuthorizationFilter}, mọi request tới được đây đã chắc chắn có phiên và
 * có ít nhất một role admin — nên không cần tự kiểm role lần nữa. Principal không phải
 * {@link SecurityPrincipal} (trường hợp lý thuyết: một cơ chế xác thực khác) thì <b>chặn</b>,
 * vì không đọc được {@code mfaLevel} nghĩa là không chứng minh được đã qua TOTP.</p>
 */
@Component
public class AdminMfaGateFilter extends OncePerRequestFilter implements AdminAccessGate {

    /** Tiền tố đúng theo p8 §8.4.12. Tính cả {@code /api/v1/admin} không có dấu gạch cuối. */
    private static final String ADMIN_PREFIX = "/api/v1/admin";

    private final MfaTotpRepository totpRepository;
    private final MessageResolver messageResolver;
    private final LocaleResolver localeResolver;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AdminMfaGateFilter(MfaTotpRepository totpRepository,
                              MessageResolver messageResolver,
                              LocaleResolver localeResolver,
                              JsonMapper jsonMapper,
                              Clock clock) {
        this.totpRepository = totpRepository;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(ADMIN_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();

        if (principal instanceof SecurityPrincipal user && user.mfaLevel() == MfaLevel.TOTP) {
            filterChain.doFilter(request, response);
            return;
        }

        ErrorCode errorCode = principal instanceof SecurityPrincipal user && enrolled(user)
                ? IdentityErrorCode.ADMIN_TOTP_REQUIRED
                : IdentityErrorCode.TOTP_SETUP_REQUIRED;
        writeProblem(request, response, errorCode);
    }

    private boolean enrolled(SecurityPrincipal user) {
        return totpRepository.findByUserId(user.userId())
                .map(MfaTotp::isActive)
                .orElse(false);
    }

    /**
     * Ghi {@code ProblemDetail} (RFC 9457) thẳng vào response: filter nằm NGOÀI
     * {@code DispatcherServlet} nên {@code @RestControllerAdvice} không bắt được exception ném
     * từ đây. Giữ đúng hình dạng body mà {@code GlobalExceptionHandler} trả về
     * ({@code type}/{@code title}/{@code detail}/{@code errorCode}/{@code timestamp}) để client
     * chỉ cần một đường xử lý lỗi.
     */
    private void writeProblem(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.status(), messageResolver.resolve(errorCode.code(), locale));
        problem.setType(errorCode.typeUri());
        problem.setTitle(errorCode.code());
        problem.setProperty("errorCode", errorCode.code());
        problem.setProperty("timestamp", clock.instant());

        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
