package com.catcheck.shared.application.spi;

public interface PublicIngressRateLimiter {

    enum Limit {

        CSP_REPORT(60),

        CLIENT_ERROR(10);

        private final int perMinute;

        Limit(int perMinute) {
            this.perMinute = perMinute;
        }

        public int perMinute() {
            return perMinute;
        }
    }

    boolean tryConsume(Limit limit, String clientIp);

    long secondsUntilRefill(Limit limit, String clientIp);
}
