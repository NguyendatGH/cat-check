package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.OtpPurpose;

/**
 * Bam ma OTP thanh HMAC-SHA256 co khoa (pepper) va ca bien muc dich vao dau vao
 * (p11 §11.2.1).
 *
 * <p>Day la CONG (interface) chu khong phai dich vu: khoa pepper nam o
 * {@code ..infrastructure..} nen {@code ..application..} khong duoc phai import
 * no (R2).</p>
 */
public interface OtpCodeHasher {

    /**
     * {@code HMAC-SHA256(pepper[pepperVersion], purpose || email || code)} — hex
     * 64 ky tu.
     *
     * <p>Truong {@code purpose} phai nam TRONG chuoi da bam: ma sinh cho mot muc
     * dich khong dung duoc cho muc dich khac.</p>
     */
    String hash(int pepperVersion, OtpPurpose purpose, String email, String code);

    /** Version pepper hien tai, dung khi tao challenge moi. */
    int currentPepperVersion();

    /**
     * Hash cho {@code pepperVersion} bat ky (dung khi xac minh: thu moi theo
     * {@code pepper_version} luu trong dong, truot fallback qua cac version cu hon).
     */
    String hashForStoredVersion(int pepperVersion, OtpPurpose purpose, String email, String code);
}
