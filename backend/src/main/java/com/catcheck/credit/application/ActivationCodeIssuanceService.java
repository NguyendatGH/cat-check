package com.catcheck.credit.application;

import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFormat;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Phát hành mã kích hoạt cho lô sản xuất — phía admin, module UI quản trị ở M6.
 *
 * <p><b>Đây là nơi duy nhất mã thô tồn tại.</b> Mã được sinh, băm, ghi {@code code_hash} vào DB
 * rồi trả về đúng một lần trong {@link IssuedBatch#rawCodes()} để xuất CSV cho khâu in bao bì
 * (p5 §5.9). Sau lần gọi này không còn cách nào đọc lại mã thô — DB chỉ còn HMAC, và HMAC là
 * hàm một chiều.</p>
 *
 * <p>Đây là lý do {@code V10} cố ý KHÔNG có cột "đã tải CSV" hay "đã in": p8 §8.4 mô tả CSV
 * một-lần-duy-nhất ở tầng UI, còn p4/p5 không dành cột nào cho việc đó. Thêm cột tự phát sẽ phá
 * vỡ schema đã chốt — xem {@code docs/handovers/A4.md}.</p>
 */
@Service
public class ActivationCodeIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(ActivationCodeIssuanceService.class);

    /**
     * Trần mã mỗi lô — <b>50 000, lấy đúng từ p8 L20</b> (<i>"≤ 50 000/lần ⇒
     * {@code 422 ACTIVATION_BATCH_TOO_LARGE}"</i>) và p14 §14.3.2 mục 4.
     *
     * <p>Bản trước đặt 10 000 "để chặn một lệnh phát hành quá lớn" — một con số tự đặt, trong
     * khi p8 sở hữu danh mục endpoint và đã chốt 50 000. Nâng được vì {@link #issue} đã chuyển
     * sang {@code insertAll} (một JDBC batch) thay vì 1 lệnh INSERT/mã.</p>
     */
    public static final int MAX_CODES_PER_BATCH = 50_000;

    private final ActivationCodePort codePort;
    private final PackagePlanPort packagePlanPort;
    private final ActivationCodeHasher codeHasher;
    private final SecureRandom secureRandom;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ActivationCodeIssuanceService(
            ActivationCodePort codePort,
            PackagePlanPort packagePlanPort,
            ActivationCodeHasher codeHasher,
            SecureRandom secureRandom,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.codePort = codePort;
        this.packagePlanPort = packagePlanPort;
        this.codeHasher = codeHasher;
        this.secureRandom = secureRandom;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * Phát hành {@code quantity} mã cho một gói.
     *
     * @param packageCode     mã gói
     * @param quantity        số mã, {@code 1..}{@value #MAX_CODES_PER_BATCH}
     * @param productionBatch lô sản xuất, liên kết bảng màu pH
     * @param validForDays    hạn KÍCH HOẠT tính từ lúc phát hành — khác hạn credit
     * @return kết quả, trong đó {@link IssuedBatch#rawCodes()} là lần xuất DUY NHẤT
     */
    @Transactional
    public IssuedBatch issue(String packageCode, int quantity, String productionBatch, int validForDays) {
        if (quantity < 1 || quantity > MAX_CODES_PER_BATCH) {
            // Lỗi tham số đầu vào của lệnh phát hành, KHÔNG phải lỗi nghiệp vụ gặp bởi người dùng
            // cuối: dùng IllegalArgumentException chứ không mượn một mã trong CreditErrorCode.
            throw new IllegalArgumentException(
                    "Số mã phải nằm trong [1, " + MAX_CODES_PER_BATCH + "], nhận: " + quantity);
        }
        if (validForDays < 1) {
            throw new IllegalArgumentException("Hạn kích hoạt phải >= 1 ngày, nhận: " + validForDays);
        }
        // p8 §8.2.4 không có mã lỗi cho "gói không tồn tại": đây là đường admin (M6) và UI chỉ
        // cho chọn gói đang bán, nên đây là vi phạm tính toàn vẹn tham chiếu chứ không phải
        // tình huống người dùng. Nếu sau này mở endpoint, W3 cần bổ sung mã lỗi vào p8.
        if (packagePlanPort.findByCode(packageCode).isEmpty()) {
            throw new NoSuchElementException("Không có gói " + packageCode + " trong package_plan");
        }

        Instant issuedAt = clock.instant();
        Instant validUntil = issuedAt.plus(validForDays, ChronoUnit.DAYS);
        String codePrefix = ActivationCodeFormat.codePrefixOf(packageCode);

        // Chống trùng TRONG LÔ bằng chính tập hash sắp ghi: UNIQUE(code_hash) ở DB vẫn là lưới
        // cuối, nhưng để nó bắt nghĩa là cả transaction 50 000 dòng bị huỷ vì một lần trùng
        // ngẫu nhiên. Sinh lại ngay tại đây rẻ hơn nhiều.
        java.util.Set<String> hashes = new java.util.HashSet<>(quantity * 2);
        java.util.List<String> rawCodes = new java.util.ArrayList<>(quantity);
        java.util.List<ActivationCode> rows = new java.util.ArrayList<>(quantity);
        while (rows.size() < quantity) {
            String raw = ActivationCodeFormat.generate(packageCode, secureRandom);
            ActivationCodeHasher.HashedActivationCode hashed = codeHasher.hash(raw);
            if (!hashes.add(hashed.hex())) {
                continue;
            }
            rows.add(new ActivationCode(
                    uuidV7.generate(), hashed.hex(), hashed.pepperVersion(), codePrefix, packageCode,
                    productionBatch, issuedAt, validUntil, ActivationCodeStatus.ISSUED, null, null));
            rawCodes.add(raw);
        }
        codePort.insertAll(rows);

        // Log KHÔNG chứa mã thô lẫn mã băm — chỉ số lượng và mã gói.
        log.info("Phát hành {} mã gói {}, lô sản xuất {}", quantity, packageCode, productionBatch);

        return new IssuedBatch(packageCode, productionBatch, issuedAt, validUntil, List.copyOf(rawCodes));
    }

    /**
     * Kết quả phát hành.
     *
     * @param rawCodes mã THÔ, trả về đúng một lần để xuất CSV. Không có đường nào đọc lại được.
     */
    public record IssuedBatch(
            String packageCode,
            String productionBatch,
            Instant issuedAt,
            Instant validUntil,
            List<String> rawCodes
    ) {

        public IssuedBatch {
            rawCodes = rawCodes == null ? List.of() : List.copyOf(rawCodes);
        }
    }
}
