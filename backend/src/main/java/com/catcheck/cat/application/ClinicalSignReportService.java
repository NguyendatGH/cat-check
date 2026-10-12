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
                .filter(cat -> !cat.isDeleted())
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }
}
