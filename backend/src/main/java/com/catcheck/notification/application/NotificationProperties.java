package com.catcheck.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catcheck.notification")
public record NotificationProperties(Push push, Outbox outbox) {

    public NotificationProperties {
        push = push == null ? new Push(null, null, null) : push;
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
    }

    public record Push(String serviceAccountPath, String projectId, Integer maxDevicesPerUser) {

        public Push {
            maxDevicesPerUser = maxDevicesPerUser == null ? 10 : maxDevicesPerUser;
        }

        public boolean configured() {
            return serviceAccountPath != null && !serviceAccountPath.isBlank();
        }
    }

    public record Outbox(Integer maxAttempts, Integer emailBatchSize, Integer pushBatchSize) {

        public Outbox {
            maxAttempts = maxAttempts == null ? 5 : maxAttempts;
            emailBatchSize = emailBatchSize == null ? 50 : emailBatchSize;
            pushBatchSize = pushBatchSize == null ? 100 : pushBatchSize;
        }
    }
}
