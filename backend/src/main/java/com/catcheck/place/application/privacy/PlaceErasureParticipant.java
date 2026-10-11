package com.catcheck.place.application.privacy;

import com.catcheck.place.domain.port.PlaceRepository;
import com.catcheck.privacy.spi.ErasureParticipant;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link ErasureParticipant} của module place (R10: nằm trong {@code ..application.privacy..}).
 *
 * <ul>
 *   <li>{@code place_booking.note} — <b>gỡ</b> (đặt NULL), giữ dòng. Ghi chú là văn bản tự do
 *       gửi cơ sở nên thường chứa tên/số điện thoại. Giữ dòng nhưng gỡ văn bản tự do là cách đã
 *       công bố cho bản ghi tín dụng khi xoá tài khoản, và V19 chỉ cấp
 *       {@code SELECT, INSERT, UPDATE} (không có {@code DELETE}) cho {@code place_booking};
 *       {@code user_id} còn lại trỏ tới {@code app_user} đã ẩn danh hoá.</li>
 *   <li>{@code place_review.body} — <b>KHÔNG đụng</b>, chưa có chính sách: p15 §15.4.6 (Giai đoạn
 *       2) cho user tự chọn giữ nội dung dạng ẩn danh hay xoá hẳn, mà
 *       {@code POST /privacy/account-deletion} chưa nhận lựa chọn đó. Tự gỡ ở đây là tự chọn
 *       thay user. Trả lời công khai đã không lộ người viết ({@code PlaceReviewResponse}).</li>
 * </ul>
 */
@Component
public class PlaceErasureParticipant implements ErasureParticipant {

    public static final String NAME = "place";

    private final PlaceRepository repository;

    public PlaceErasureParticipant(PlaceRepository repository) {
        this.repository = repository;
    }

    @Override
    public String participantName() {
        return NAME;
    }

    @Override
    public void erase(UUID subjectId) {
        repository.clearBookingNotes(subjectId);
    }
}
