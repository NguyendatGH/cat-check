package com.catcheck.notification.infrastructure.push;

import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushTarget;
import com.catcheck.notification.domain.port.PushMessageSender;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gửi web push qua {@code firebase-admin} 9.11.0.
 *
 * <p><b>Ba quyết định cài đặt, kèm lý do:</b></p>
 * <ol>
 *   <li><b>{@code sendEachForMulticast}, không loop từng thiết bị</b> (p12 §12.3.2). Một user
 *       có thể có điện thoại + desktop Chrome; loop tuần tự là N round-trip.</li>
 *   <li><b>Tách HAI lượt multicast: một cho FID, một cho registration token.</b>
 *       {@code MulticastMessage.Builder} giữ FID và token trong hai danh sách riêng, và
 *       {@code BatchResponse.getResponses()} khớp theo thứ tự {@code getMessageList()} — trộn
 *       hai loại trong một message làm việc map kết quả → thiết bị phụ thuộc vào thứ tự nội bộ
 *       của builder, thứ không có trong hợp đồng công khai. Hai lượt thì thứ tự là hiển
 *       nhiên.</li>
 *   <li><b>Payload dạng {@code data} thuần</b>, KHÔNG dùng {@code setNotification(...)}
 *       (p12 §12.3.6): service worker tự quyết định hiển thị, tránh trình duyệt render mặc định
 *       một nội dung mà p12 §12.2.1 cấm đưa lên màn khoá.</li>
 * </ol>
 *
 * <p><b>Không log nội dung, FID hay token</b> — chỉ log mã lỗi và số lượng (p17 §17.10b;
 * ArchUnit R16 cũng chặn đọc field tên khớp {@code fid}/{@code token} trong method có gọi
 * logger).</p>
 */
class FirebaseCloudMessagingSender implements PushMessageSender {

    private static final Logger log = LoggerFactory.getLogger(FirebaseCloudMessagingSender.class);

    /** Trần của một lần {@code sendEachForMulticast} theo tài liệu FCM. */
    private static final int MAX_TARGETS_PER_MULTICAST = 500;

    private final FirebaseMessaging messaging;

    FirebaseCloudMessagingSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public List<Result> send(List<PushTarget> targets, PushPayload payload) {
        List<Result> results = new ArrayList<>(targets.size());
        Map<String, String> data = payload.asDataMap();
        results.addAll(sendGroup(targets.stream().filter(PushTarget::byInstallationId).toList(), data, true));
        results.addAll(sendGroup(targets.stream().filter(target -> !target.byInstallationId()).toList(), data, false));
        return results;
    }

    private List<Result> sendGroup(List<PushTarget> group, Map<String, String> data, boolean byInstallationId) {
        List<Result> results = new ArrayList<>(group.size());
        for (int from = 0; from < group.size(); from += MAX_TARGETS_PER_MULTICAST) {
            List<PushTarget> chunk = group.subList(from,
                    Math.min(from + MAX_TARGETS_PER_MULTICAST, group.size()));
            results.addAll(sendChunk(chunk, data, byInstallationId));
        }
        return results;
    }

    private List<Result> sendChunk(List<PushTarget> chunk, Map<String, String> data, boolean byInstallationId) {
        if (chunk.isEmpty()) {
            return List.of();
        }
        MulticastMessage.Builder builder = MulticastMessage.builder().putAllData(data);
        List<String> addresses = chunk.stream().map(PushTarget::value).toList();
        if (byInstallationId) {
            builder.addAllFids(addresses);
        } else {
            builder.addAllTokens(addresses);
        }
        try {
            BatchResponse response = messaging.sendEachForMulticast(builder.build());
            return mapResponses(chunk, response);
        } catch (FirebaseMessagingException ex) {
            // Lỗi của CẢ lượt gọi (hết hạn credential, mạng...) — lỗi tạm cho mọi đích, đừng
            // thu hồi subscription nào.
            log.warn("sendEachForMulticast thất bại cho {} thiết bị: {}", chunk.size(),
                    ex.getMessagingErrorCode());
            String code = ex.getMessagingErrorCode() == null ? "UNKNOWN" : ex.getMessagingErrorCode().name();
            return chunk.stream()
                    .map(target -> new Result(target.subscriptionId(), Status.TRANSIENT_FAILURE,
                            null, code, ex.getMessage()))
                    .toList();
        }
    }

    private List<Result> mapResponses(List<PushTarget> chunk, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        List<Result> results = new ArrayList<>(chunk.size());
        for (int index = 0; index < chunk.size(); index++) {
            PushTarget target = chunk.get(index);
            if (index >= responses.size()) {
                results.add(new Result(target.subscriptionId(), Status.TRANSIENT_FAILURE, null,
                        "MISSING_RESPONSE", "FCM trả thiếu response cho đích này"));
                continue;
            }
            SendResponse single = responses.get(index);
            if (single.isSuccessful()) {
                results.add(Result.delivered(target.subscriptionId(), single.getMessageId()));
                continue;
            }
            FirebaseMessagingException failure = single.getException();
            MessagingErrorCode errorCode = failure == null ? null : failure.getMessagingErrorCode();
            results.add(new Result(target.subscriptionId(), statusOf(errorCode), null,
                    errorCode == null ? "UNKNOWN" : errorCode.name(),
                    failure == null ? null : failure.getMessage()));
        }
        return results;
    }

    /**
     * p12 §12.3.9 / research §5.5: {@code UNREGISTERED} và {@code INVALID_ARGUMENT} là chết hẳn
     * ⇒ thu hồi ngay, không retry. Còn lại coi là lỗi tạm.
     */
    private Status statusOf(MessagingErrorCode errorCode) {
        if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            return Status.PERMANENT_FAILURE;
        }
        return Status.TRANSIENT_FAILURE;
    }
}
