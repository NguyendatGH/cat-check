package com.catcheck.scan.domain.color;

import java.util.List;

/**
 * Layout thẻ tham chiếu màu — bản sao <b>chỉ đọc</b> phía scan của JSON
 * {@code reference_card_layout.layout} (p4 D8), đủ để lấy mẫu patch cho CARD_CCM (p6 §6.5.1 S4).
 *
 * <p>Cố tình không mang trọn JSON (marker ArUco/QR, payload…) — scan chỉ cần toạ độ + vai trò +
 * màu tham chiếu của từng patch để lấy mẫu và giải CCM; phần còn lại thuộc về
 * {@code VisionEngine#detectCard} (đã tự biết ID marker 0..3) và module {@code colorchart}
 * (quản lý/validate layout). Cùng nguyên tắc tách kiểu với {@link PhChart}: kiểu ở đây sống trong
 * RAM phía scan, không mang ID bản ghi DB nào.
 *
 * @param code            mã thẻ (vd {@code CC-CARD})
 * @param version         phiên bản layout
 * @param canonicalWidth  chiều rộng khung chuẩn hoá (px) mà {@code rect} của mỗi patch tính theo
 * @param canonicalHeight chiều cao khung chuẩn hoá (px)
 * @param patches         danh sách patch, toạ độ {@code rect} chuẩn hoá {@code [0,1]}
 */
public record CardLayout(String code, int version, int canonicalWidth, int canonicalHeight, List<Patch> patches) {

    public CardLayout {
        if (patches == null || patches.isEmpty()) {
            throw new IllegalArgumentException("Layout the phai co it nhat mot patch");
        }
        patches = List.copyOf(patches);
    }

    /** Vai trò của một patch trên thẻ (p4 D8). */
    public enum Role {
        /** Ô xám trung tính — dải linearization + CCM (S4). */
        NEUTRAL,
        /** Ô màu bão hoà — tham khảo, không dùng cho CCM ở M3 (chỉ patch NEUTRAL mới đủ ổn định). */
        COLOR,
        /** Ô mẫu một mức pH in sẵn trên thẻ — đối chiếu S8, không dùng cho CCM. */
        PH_LEVEL
    }

    /**
     * Một ô màu trên thẻ.
     *
     * @param id     mã patch (vd {@code W1}, {@code G80})
     * @param x      gốc trái, chuẩn hoá {@code [0,1]} theo {@link #canonicalWidth}
     * @param y      gốc trên, chuẩn hoá {@code [0,1]} theo {@link #canonicalHeight}
     * @param w      chiều rộng, chuẩn hoá {@code [0,1]}
     * @param h      chiều cao, chuẩn hoá {@code [0,1]}
     * @param role   vai trò patch
     * @param refLab màu tham chiếu CIELAB D65/2° in trên thẻ
     */
    public record Patch(String id, double x, double y, double w, double h, Role role, Lab refLab) {
    }

    /** Các patch {@code role=NEUTRAL} — nguồn mẫu duy nhất cho CARD_CCM ở M3 (p6 §6.5.1 S4). */
    public List<Patch> neutralPatches() {
        return patches.stream().filter(p -> p.role() == Role.NEUTRAL).toList();
    }
}
