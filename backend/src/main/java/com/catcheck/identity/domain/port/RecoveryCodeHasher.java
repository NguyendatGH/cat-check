package com.catcheck.identity.domain.port;

public interface RecoveryCodeHasher {

    String hash(int pepperVersion, String recoveryCode);

    int currentPepperVersion();

    boolean matches(String recoveryCode, String codeHash, int pepperVersion);
}
