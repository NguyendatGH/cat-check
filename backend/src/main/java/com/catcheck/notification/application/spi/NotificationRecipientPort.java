package com.catcheck.notification.application.spi;

import com.catcheck.notification.domain.NotificationRecipient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Đọc {@code app_user.email} / {@code locale} / {@code timezone} để điền
 * {@code email_outbox.to_address} và tính quiet hours theo giờ địa phương (p12 §12.5.2).
 *
 * <p>Cùng lý do như {@link ConsentGatePort}: đọc bằng JDBC, không import {@code identity}.</p>
 */
public interface NotificationRecipientPort {

    Optional<NotificationRecipient> findById(UUID userId);

    /**
     * L72 — id cua moi chu the du dieu kien nhan thong bao he thong, phan trang bang
     * <b>keyset</b> theo {@code id}.
     *
     * <p><b>Keyset chu khong {@code OFFSET}:</b> fan-out cua L72 di qua nhieu lo, va giua hai lo
     * co the co tai khoan moi dang ky hoac tai khoan bi an danh hoa. Voi {@code OFFSET}, mot dong
     * bi chen/xoa o giua lam lech het cac trang sau — tuc la co nguoi bi gui doi va co nguoi
     * khong duoc gui, tren mot thong bao ma p12 §12.2.5 goi la "bat buoc gui cho MOI chu the bi
     * anh huong". {@code id} la UUID v7 nen tang don theo thoi gian tao, keyset chay dung.</p>
     *
     * <p>Bo loc trang thai lay nguyen tu {@link #findById}: khong gui cho {@code ANONYMIZED} (da
     * khong con email) va {@code DELETION_REQUESTED} (dang trong thoi gian cho xoa). Tai khoan
     * {@code LOCKED}/{@code RESTRICTED} <b>van nhan</b> — p12 §12.2.5 ghi ro
     * {@code PRIVACY_INCIDENT_NOTICE} phai den ca tai khoan dang {@code RESTRICTED}.</p>
     *
     * @param afterId chi lay id LON HON gia tri nay; {@code null} = tu dau
     * @param limit   kich thuoc lo
     * @return danh sach id tang dan, rong khi het
     */
    List<UUID> findActiveRecipientIdsAfter(UUID afterId, int limit);
}
