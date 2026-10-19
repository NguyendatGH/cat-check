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

class FirebaseCloudMessagingSender implements PushMessageSender {

    private static final Logger log = LoggerFactory.getLogger(FirebaseCloudMessagingSender.class);

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

    private Status statusOf(MessagingErrorCode errorCode) {
        if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            return Status.PERMANENT_FAILURE;
        }
        return Status.TRANSIENT_FAILURE;
    }
}
