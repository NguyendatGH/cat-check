package com.catcheck.identity.domain.port;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);

    String dummyHash();
}
