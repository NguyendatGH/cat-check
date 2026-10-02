package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.api.ClinicalSignsReportedEvent;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.CatClinicalSignReport;
import com.catcheck.cat.domain.ClinicalSign;
import com.catcheck.cat.domain.ClinicalSignSource;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.cat.domain.port.ClinicalSignReportRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Báo cáo dấu hiệu lâm sàng do chủ nuôi tự khai — D20 của p8 §8.4.4.
 *
 * <p><b>Không tự sinh {@code health_flag}.</b> {@code ClinicalSignsReportedEvent} là điểm nối cho
 * module {@code insight} (chưa tồn tại — xem {@code cat/api/ClinicalSignsReportedEvent.java}), nên
 * {@code triggeredFlag} trong response D20 LUÔN {@code null} cho tới khi insight lắng nghe sự kiện
 * này và tự ghi lại qua {@link com.catcheck.cat.domain.CatClinicalSignReport#linkHealthFlag}.
 * KHÔNG tự làm việc của insight ở đây.</p>
 *
 * <p><b>{@code blockingShown} luôn {@code true}.</b> FE không gửi cờ này trong request (xem
 * {@code docs/handovers/A3-fe.md} — {@code ClinicalSignEmergencyNotice} "luôn mở, không được rút
 * gọn" là điều kiện tiên quyết để form D20 hiện được nút gửi), nên phía server coi việc gọi được
 * endpoint này đã đồng nghĩa màn cảnh báo đã hiện.</p>
 */
@Service
public class ClinicalSignReportService {

    private final ClinicalSignReportRepository reportRepository;
    private final CatRepository catRepository;
    private final UserAccountPort userAccountPort;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ClinicalSignReportService(
            ClinicalSignReportRepository reportRepository,
            CatRepository catRepository,
            UserAccountPort userAccountPort,
            ApplicationEventPublisher eventPublisher,
            UuidV7 uuidV7,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.catRepository = catRepository;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * D20 — lưu bản khai.
     *
     * @param rawSigns tên hằng số {@link ClinicalSign} dạng chuỗi, có thể chứa giá trị lạ (đến từ
     *                 client) — validate ở đây để trả {@code 400 CLINICAL_SIGN_INVALID} kèm
     *                 {@code allowed[]} thay vì để domain ném {@code IllegalArgumentException}
     * @param rawSource tên hằng số {@link ClinicalSignSource} dạng chuỗi
     */
    @Transactional
    public CatClinicalSignReport create(UUID ownerId, UUID catId, Set<String> rawSigns, String rawSource) {
        requireWriteAllowed(ownerId);
        requireOwnedCat(ownerId, catId);

        Set<ClinicalSign> signs = parseSigns(rawSigns);
        ClinicalSignSource source = parseSource(rawSource);

        Instant now = clock.instant();
        CatClinicalSignReport report = CatClinicalSignReport.create(
                uuidV7.generate(), catId, ownerId, signs, source, true, now);
        CatClinicalSignReport saved = reportRepository.save(report);

        eventPublisher.publishEvent(new ClinicalSignsReportedEvent(
                saved.getId(), catId, ownerId,
                signs.stream().map(Enum::name).collect(LinkedHashSet::new, Set::add, Set::addAll),
                source.name(), true, now));
        return saved;
    }

    private Set<ClinicalSign> parseSigns(Set<String> rawSigns) {
        if (rawSigns == null || rawSigns.isEmpty()) {
            throw new BusinessRuleException(
                    CatErrorCode.CLINICAL_SIGN_INVALID, (Object) ClinicalSign.values());
        }
        Set<ClinicalSign> parsed = new LinkedHashSet<>();
        for (String raw : rawSigns) {
            parsed.add(Arrays.stream(ClinicalSign.values())
                    .filter(candidate -> candidate.name().equalsIgnoreCase(raw))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            CatErrorCode.CLINICAL_SIGN_INVALID, (Object) ClinicalSign.values())));
        }
        return parsed;
    }

    /**
     * Bug that da sua (thong diep sai mien): truoc day ca hai nhanh nem
     * {@code CLINICAL_SIGN_INVALID} — ma message cua no la "Vui long chon it nhat mot dau
     * hieu", noi ve DANH SACH DAU HIEU chu khong phai ve {@code source}. Client gui sai
     * {@code source} (vd "APP") nhan 400 kem loi chi sai cho, khong biet duong sua. Dung
     * {@code VALIDATION_FAILED} kem ten truong, giong cach {@code PolicyService} lam.
     */
    private ClinicalSignSource parseSource(String rawSource) {
        if (rawSource == null || rawSource.isBlank()) {
            throw new BusinessRuleException(CatErrorCode.VALIDATION_FAILED, "source");
        }
        return Arrays.stream(ClinicalSignSource.values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(rawSource))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        CatErrorCode.VALIDATION_FAILED, "source"));
    }

    private void requireOwnedCat(UUID ownerId, UUID catId) {
        catRepository.findByIdAndOwnerId(catId, ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }
}
