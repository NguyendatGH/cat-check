package com.catcheck.credit.domain.port;

public interface ActivationCodeHasher {

    HashedActivationCode hash(String normalizedCode);

    record HashedActivationCode(String hex, short pepperVersion) {

        public HashedActivationCode {
            if (hex == null || hex.length() != 64) {
                throw new IllegalArgumentException("hash phải đúng 64 ký tự hex");
            }
            if (pepperVersion < 1) {
                throw new IllegalArgumentException("pepperVersion phải >= 1");
            }
        }
    }
}
