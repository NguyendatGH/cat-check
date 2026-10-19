package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.OtpPurpose;

public interface OtpCodeHasher {

    String hash(int pepperVersion, OtpPurpose purpose, String email, String code);

    int currentPepperVersion();

    String hashForStoredVersion(int pepperVersion, OtpPurpose purpose, String email, String code);
}
