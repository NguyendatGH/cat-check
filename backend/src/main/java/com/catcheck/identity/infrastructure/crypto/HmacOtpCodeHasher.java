package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.port.OtpCodeHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * {@code HMAC-SHA256(pepper, purpose || email || code)} — p11 §11.2.1.
 *
 * <p>Dinh dang dau vao la chuoi nho phai <b>khong phân biet</b>: dung chinh xac
 * {@code purpose + email + code} o ca hai noi (bam va xac minh). Dung mot ham
 * {@link #digest} de ca hai duong dung chung mot quy uoc — tranh truong hop
 * "bam o noi nay, verify o noi kia" lech mot ky tu.</p>
 *
 * <p>KHONG dung SHA-256 trong ma: khong co khoa, nen bang 100% mau danh sach
 * (p11 §11.2.1 va §11.7.4 cung nhat tinh huong nay).</p>
 */
@Component
public class HmacOtpCodeHasher implements OtpCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final PepperRing ring;

    public HmacOtpCodeHasher(@Value("${OTP_PEPPER:}") String pepper,
                             @Value("${OTP_PEPPER_VERSION:}") String version) {
        this.ring = PepperRing.of("OTP_PEPPER", pepper, version);
    }

    @Override
    public String hash(int pepperVersion, OtpPurpose purpose, String email, String code) {
        return HexFormat.of().formatHex(mac(pepperVersion, purpose, email, code));
    }

    @Override
    public int currentPepperVersion() {
        return ring.currentVersion();
    }

    @Override
    public String hashForStoredVersion(int pepperVersion, OtpPurpose purpose, String email, String code) {
        if (!ring.knows(pepperVersion)) {
            // Khong fallback: ma viet bang pepper da rut se het hieu luc, dung la
            // chu dung. Nem de loi "khong xac minh duoc" thay vi so sanh mot chuoi
            // bam bang mot pepper khac (luon ra khac ket qua -> luon fail, nhung
            // thong diep loi se lam nguoi van hoang).
            throw new IllegalStateException(
                    "OTP co pepper_version=" + pepperVersion + " ma keyring hien tai khong co ("
                            + ring.knownVersions().keySet() + ")");
        }
        return hash(pepperVersion, purpose, email, code);
    }

    private byte[] mac(int pepperVersion, OtpPurpose purpose, String email, String code) {
        byte[] key = ring.keyFor(pepperVersion);
        byte[] message = (purpose.name() + email + code).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Khong tinh duoc HMAC-SHA256 cho ma OTP", ex);
        }
    }
}
