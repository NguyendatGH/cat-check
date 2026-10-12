package com.catcheck.credit.infrastructure.crypto;

import com.catcheck.credit.domain.port.ActivationCodeHasher;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class HmacActivationCodeHasher implements ActivationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private static final int MIN_PEPPER_BYTES = 32;

    private final byte[] pepperKey;
    private final short pepperVersion;

    public HmacActivationCodeHasher(ActivationPepperProperties properties) {
        byte[] pepper = properties.pepper().getBytes(StandardCharsets.UTF_8);
        if (pepper.length < MIN_PEPPER_BYTES) {
            throw new IllegalStateException(
                    "ACTIVATION_PEPPER phải dài tối thiểu " + MIN_PEPPER_BYTES + " byte, nhận: " + pepper.length);
        }
        this.pepperKey = pepper;
        this.pepperVersion = properties.pepperVersion();
    }

    @Override
    public HashedActivationCode hash(String normalizedCode) {
        if (normalizedCode == null || normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Mã rỗng không thể băm");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepperKey, ALGORITHM));
            return new HashedActivationCode(HEX.formatHex(mac.doFinal(
                    normalizedCode.getBytes(StandardCharsets.UTF_8))), pepperVersion);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không khởi tạo được " + ALGORITHM, ex);
        }
    }

}
