package com.catcheck.privacy.spi;

import java.util.UUID;

/**
 * Hợp đồng SPI mà một module có dữ liệu cá nhân của người dùng implement để tham gia quy trình
 * xoá dữ liệu (do module privacy điều phối, ví dụ khi người dùng yêu cầu xoá tài khoản).
 *
 * <p>Theo ArchUnit R10: mọi class implement interface này phải nằm trong package con
 * {@code ..application.privacy..} của module implement (ví dụ
 * {@code com.catcheck.cat.application.privacy.CatErasureParticipant}), KHÔNG đặt trong domain
 * hay infrastructure. Ở M0 chưa có module nghiệp vụ nào implement interface này — rule R10
 * chưa có instance nào để kiểm tra, vẫn PASS (đúng theo thiết kế, không phải bug).</p>
 */
public interface ErasureParticipant {

    /** Định danh module để privacy log/theo dõi tiến trình xoá (ví dụ "cat", "scan"). */
    String participantName();

    /** Xoá (hoặc ẩn danh hoá) toàn bộ dữ liệu cá nhân của subjectId thuộc phạm vi module này. */
    void erase(UUID subjectId);
}
