package com.catcheck.ai.application.privacy;

import com.catcheck.ai.application.AiKnowledgePort;
import com.catcheck.privacy.spi.ErasureParticipant;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link ErasureParticipant} của module ai (R10: nằm trong {@code ..application.privacy..}) — cùng
 * khuôn {@code place.application.privacy.PlaceErasureParticipant}.
 *
 * <ul>
 *   <li>{@code ai_message.content} — <b>gỡ</b> (thay bằng dấu cố định), giữ dòng. Cả câu hỏi lẫn
 *       câu trả lời: câu hỏi được lưu nguyên văn (có thể có email/SĐT/tên — redactor chỉ chạy trên
 *       bản gửi LLM), câu trả lời có thể nhắc tên mèo (vd mẫu khẩn cấp).</li>
 *   <li>Vì sao KHÔNG {@code DELETE}: V21 không cấp quyền nào cho {@code catcheck_app} trên
 *       {@code ai_*}, và các bảng cùng loại cấp {@code SELECT, INSERT, UPDATE} không có
 *       {@code DELETE} (V19 {@code place_booking}, V23) — chọn thao tác chạy được trong khuôn quyền
 *       đó, giống cách xử lý {@code place_booking.note}. {@code ai_conversation} (id, user_id, mốc
 *       thời gian) và {@code ai_citation} (id tài liệu, thứ hạng) không có nội dung tự do;
 *       {@code user_id} còn lại trỏ tới {@code app_user} đã ẩn danh hoá.</li>
 * </ul>
 */
@Component
public class AiErasureParticipant implements ErasureParticipant {

    public static final String NAME = "ai";

    private final AiKnowledgePort repository;

    public AiErasureParticipant(AiKnowledgePort repository) {
        this.repository = repository;
    }

    @Override
    public String participantName() {
        return NAME;
    }

    @Override
    public void erase(UUID subjectId) {
        repository.eraseConversationContent(subjectId);
    }
}
