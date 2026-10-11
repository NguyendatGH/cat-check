package com.catcheck.shared.error;

/**
 * Bản sao để GHI LOG của một exception: giữ tên lớp và stack frame của cả chuỗi {@code cause},
 * bỏ mọi message.
 *
 * <p>Message của exception từ framework, JDBC hay dịch vụ ngoài có thể mang giá trị của request
 * (ví dụ {@code Key (email)=(...)} của PostgreSQL, địa chỉ người nhận trong lỗi SMTP) và tầng log
 * không có cách lọc tin cậy. Stack frame chỉ là tên lớp/method/dòng code — đủ để debug một lỗi
 * 500 hay một job hỏng mà không đưa dữ liệu người dùng vào log.</p>
 */
public final class RedactedThrowable extends Throwable {

    /** Chặn chuỗi {@code cause} vòng tròn ({@code initCause} chỉ cấm tự trỏ vào chính nó). */
    private static final int MAX_CAUSE_DEPTH = 16;

    private RedactedThrowable(String originalType, Throwable cause) {
        super(originalType, cause, false, true);
    }

    public static Throwable of(Throwable source) {
        return copy(source, 0);
    }

    private static RedactedThrowable copy(Throwable source, int depth) {
        Throwable cause = source.getCause();
        RedactedThrowable redactedCause = cause == null || cause == source || depth >= MAX_CAUSE_DEPTH
                ? null
                : copy(cause, depth + 1);
        RedactedThrowable copy = new RedactedThrowable(source.getClass().getName(), redactedCause);
        copy.setStackTrace(source.getStackTrace());
        return copy;
    }
}
