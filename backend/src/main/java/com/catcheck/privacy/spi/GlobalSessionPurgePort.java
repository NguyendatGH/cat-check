package com.catcheck.privacy.spi;

public interface GlobalSessionPurgePort {

    PurgeOutcome purgeEverySession();

    record PurgeOutcome(int httpSessionsDeleted, int deviceSessionsRevoked, int otpInvalidated) {

        public static PurgeOutcome none() {
            return new PurgeOutcome(0, 0, 0);
        }
    }
}
