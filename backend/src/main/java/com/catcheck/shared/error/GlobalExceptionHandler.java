package com.catcheck.shared.error;

import com.catcheck.shared.i18n.MessageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * Chuyển mọi exception thành {@link ProblemDetail} (RFC 9457) — dùng ProblemDetail của Spring 7,
 * KHÔNG tự viết class ProblemDetail riêng, theo đúng yêu cầu ở CLAUDE.md/p7.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messageResolver;
    private final Clock clock;

    public GlobalExceptionHandler(MessageResolver messageResolver, Clock clock) {
        this.messageResolver = messageResolver;
        this.clock = clock;
    }

    @ExceptionHandler(CatCheckException.class)
    public ProblemDetail handleCatCheckException(CatCheckException ex, Locale locale) {
        ErrorCode errorCode = ex.errorCode();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                errorCode.status(), messageResolver.resolve(errorCode.code(), ex.args(), locale));
        problemDetail.setType(errorCode.typeUri());
        problemDetail.setTitle(errorCode.code());
        problemDetail.setProperty("errorCode", errorCode.code());
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        // Chỉ trả field + message đã dịch, KHÔNG trả rejected value (có thể chứa PII).
        List<ValidationViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError fieldError) -> new ValidationViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setProperty("violations", violations);
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    /**
     * Route khong ton tai → 404, KHONG phai 500.
     *
     * <p>Bug that da sua: {@code handleUnexpected} ben duoi nuot ca
     * {@code NoResourceFoundException}, nen moi endpoint co trong spec nhung chua implement
     * (F3 {@code /reference/packages}, F4 {@code /reference/monitoring-rules}, F9/F10
     * {@code /policies/{code}/versions}) tra 500 — nhin nhu server hong thay vi "chua co".
     * Sai ca goc do bao mat: 500 kem stack trace vao log cho moi lan go sai URL.</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, messageResolver.resolve("error.not-found", locale));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    /**
     * Bat bien mien bi vi pham boi DU LIEU NGUOI DUNG → 400, khong phai 500.
     *
     * <p>Bug that da sua: {@code Cat#applyAgeSource} nem {@code IllegalArgumentException} tho
     * cho 4 truong hop nguoi dung nhap duoc (thieu ca birthDate lan approxAgeMonths, cung luc
     * co ca hai, birthDate tuong lai, so thang ngoai khoang) — khong co handler nen ra 500.
     * Man tao ho so meo cho phep de trong ngay sinh, tuc la user binh thuong cham thang vao
     * loi 500 nay.</p>
     */
    /**
     * Multipart thieu part bat buoc → 400. Truoc day roi vao {@code handleUnexpected} thanh 500:
     * client gui thieu part ({@code POST /scans} can ca {@code metadata} lan {@code image}) bi
     * bao "loi may chu" thay vi "ban gui thieu du lieu", va moi lan nhu vay ghi mot stack trace
     * ERROR vao log.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("violations",
                List.of(new ValidationViolation(ex.getRequestPartName(), "required")));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, Locale locale) {
        log.warn("Vi pham bat bien mien: {}", ex.getClass().getSimpleName());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, Locale locale) {
        // Bug thật đã sửa: handler này trước đây KHÔNG log gì cả — mọi lỗi 500 ngoài dự kiến
        // biến mất hoàn toàn khỏi log, không cách nào debug được (phát hiện lúc test đăng ký
        // thật local: request trả 500 nhưng log rỗng). Không log message của `ex` trực tiếp nếu
        // exception đến từ tầng có thể mang giá trị nhạy cảm — nhưng đây là nhánh "không mong
        // muốn" (mọi CatCheckException nghiệp vụ đã có handler riêng ở trên), nên đây luôn là
        // lỗi lập trình/hạ tầng (NPE, lỗi SQL, bean thiếu...), không phải luồng nghiệp vụ đọc PII.
        log.error("Loi khong mong muon xu ly {}", ex.getClass().getSimpleName(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, messageResolver.resolve("error.unexpected", locale));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    private record ValidationViolation(String field, String message) {
    }
}
