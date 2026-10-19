package com.catcheck.cat.application;

import com.catcheck.cat.api.CatArchivedEvent;
import com.catcheck.cat.api.CatCreatedEvent;
import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.api.CatUpdatedEvent;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatAgeConflictException;
import com.catcheck.cat.domain.CatBreed;
import com.catcheck.cat.domain.CatSex;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.port.CatBreedRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.AppSettingPort;
import com.catcheck.cat.application.spi.CatProfileLimit;
import com.catcheck.cat.application.spi.CatProfileLimitPort;
import com.catcheck.cat.application.spi.OnboardingMilestone;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CatProfileService {

    public static final String HARD_CAP_SETTING_KEY = "cat.max_per_user";

    static final int DEFAULT_HARD_CAP = 8;

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "createdAt", "lastScanAt");

    private static final int MAX_PUBLIC_CODE_ATTEMPTS = 8;

    private final CatRepository catRepository;
    private final CatBreedRepository catBreedRepository;
    private final UserAccountPort userAccountPort;
    private final CatProfileLimitPort catProfileLimitPort;
    private final AppSettingPort appSettingPort;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7 uuidV7;
    private final Clock clock;
    private final Cat.Entropy publicCodeEntropy;

    public CatProfileService(
            CatRepository catRepository,
            CatBreedRepository catBreedRepository,
            UserAccountPort userAccountPort,
            CatProfileLimitPort catProfileLimitPort,
            AppSettingPort appSettingPort,
            ApplicationEventPublisher eventPublisher,
            UuidV7 uuidV7,
            Clock clock) {
        this.catRepository = catRepository;
        this.catBreedRepository = catBreedRepository;
        this.userAccountPort = userAccountPort;
        this.catProfileLimitPort = catProfileLimitPort;
        this.appSettingPort = appSettingPort;
        this.eventPublisher = eventPublisher;
        this.uuidV7 = uuidV7;
        this.clock = clock;
        this.publicCodeEntropy = bound -> SecureRandomHolder.RANDOM.nextInt(bound);
    }


    @Transactional(readOnly = true)
    public List<Cat> listForOwner(UUID ownerId, CatStatus status, String sort) {
        CatStatus effectiveStatus = status == null ? CatStatus.ACTIVE : status;
        requireSortable(sort);
        return catRepository.findAllByOwnerId(ownerId, effectiveStatus, false);
    }


    @Transactional
    public Cat create(UUID ownerId, CreateCatCommand command) {
        requireWriteAllowed(ownerId);

        long currentCount = catRepository.countActiveByOwnerId(ownerId);
        assertWithinLimits(ownerId, currentCount);

        if (command.breedCode() != null) {
            requireKnownBreed(command.breedCode());
        }

        Instant now = now();
        boolean isFirstCat = currentCount == 0;
        boolean wantsPrimary = isFirstCat || command.isPrimary();

        UUID previousPrimary = wantsPrimary
                ? catRepository.findPrimaryByOwnerId(ownerId).map(Cat::getId).orElse(null)
                : null;
        if (wantsPrimary) {
            catRepository.clearPrimaryForOwner(ownerId, now);
        }

        Cat cat;
        try {
            cat = Cat.create(
                    uuidV7.generate(),
                    ownerId,
                    command.name(),
                    command.birthDate(),
                    command.approxAgeMonths(),
                    newPublicCode(),
                    LocalDate.now(clock),
                    now);
        } catch (CatAgeConflictException ex) {
            throw new BusinessRuleException(CatErrorCode.CAT_AGE_CONFLICT);
        }
        cat.applyProfile(command.breedCode(), command.breedOther(), command.coatColor(),
                command.sex(), command.neutered(), command.weightKg(), command.notes(),
                LocalDate.now(clock), now);
        if (wantsPrimary) {
            cat.makePrimary(now);
        }

        Cat saved = catRepository.save(cat);
        userAccountPort.advanceOnboardingStatus(ownerId, OnboardingMilestone.CAT_CREATED);
        eventPublisher.publishEvent(new CatCreatedEvent(
                saved.getId(), ownerId, saved.getPublicCode(), isFirstCat, wantsPrimary, now));
        return saved;
    }


    @Transactional(readOnly = true)
    public Cat get(UUID ownerId, UUID catId) {
        return requireOwnedCat(ownerId, catId);
    }


    @Transactional
    public Cat patch(UUID ownerId, UUID catId, PatchCatCommand command) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);

        Set<String> changed = new LinkedHashSet<>();
        if (command.hasName()) {
            changed.add("name");
        }
        if (command.touchesAge()) {
            changed.add("age");
        }
        if (command.breedCode() != null) {
            requireKnownBreed(command.breedCode());
            changed.add("breedCode");
        }
        if (command.hasAnyProfileField()) {
            changed.add("profile");
        }
        if (command.weightKg() != null) {
            changed.add("weightKg");
        }

        try {
            cat.update(
                    command.hasName() ? command.name() : null,
                    command.birthDate(),
                    command.touchesAge(),
                    command.approxAgeMonths(),
                    command.breedCode(),
                    command.breedOther(),
                    command.coatColor(),
                    command.sex(),
                    command.neutered(),
                    command.weightKg(),
                    command.notes(),
                    LocalDate.now(clock),
                    now());
        } catch (CatAgeConflictException ex) {
            throw new BusinessRuleException(CatErrorCode.CAT_AGE_CONFLICT);
        }

        Cat saved = catRepository.save(cat);
        eventPublisher.publishEvent(new CatUpdatedEvent(
                saved.getId(), ownerId, changed, null, now()));
        return saved;
    }


    @Transactional
    public void softDelete(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCatIncludingDeleted(ownerId, catId);
        if (cat.isDeleted()) {
            throw new ConflictException(CatErrorCode.CAT_ALREADY_DELETED);
        }
        Instant now = now();
        cat.softDelete(now);
        catRepository.save(cat);
        eventPublisher.publishEvent(new CatArchivedEvent(catId, ownerId, true, now));
    }


    @Transactional
    public Cat archive(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
        if (cat.isArchived()) {
            throw new ConflictException(CatErrorCode.CAT_ALREADY_ARCHIVED);
        }
        Instant now = now();
        String previousStatus = cat.getStatus().name();
        cat.archive(now);
        Cat saved = catRepository.save(cat);
        eventPublisher.publishEvent(new CatUpdatedEvent(
                saved.getId(), ownerId, Set.of("status"), previousStatus, now));
        return saved;
    }

    @Transactional
    public Cat unarchive(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
        if (!cat.isArchived()) {
            return cat;
        }
        Instant now = now();
        String previousStatus = cat.getStatus().name();
        cat.unarchive(now);
        Cat saved = catRepository.save(cat);
        eventPublisher.publishEvent(new CatUpdatedEvent(
                saved.getId(), ownerId, Set.of("status"), previousStatus, now));
        return saved;
    }


    @Transactional
    public PrimaryCatResult setPrimary(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCatIncludingDeleted(ownerId, catId);
        if (cat.isArchived() || cat.isDeleted()) {
            throw new ConflictException(CatErrorCode.CAT_PRIMARY_REQUIRES_ACTIVE);
        }
        if (cat.isPrimary()) {
            throw new ConflictException(CatErrorCode.CAT_ALREADY_PRIMARY);
        }
        UUID previousPrimary = catRepository.findPrimaryByOwnerId(ownerId)
                .map(Cat::getId)
                .orElse(null);
        Instant now = now();
        catRepository.clearPrimaryForOwner(ownerId, now);
        cat.makePrimary(now);
        catRepository.save(cat);
        eventPublisher.publishEvent(new CatUpdatedEvent(
                catId, ownerId, Set.of("isPrimary"), null, now));
        return new PrimaryCatResult(catId, previousPrimary);
    }


    @Transactional(readOnly = true)
    public Optional<CatBreed> findBreed(String breedCode) {
        return breedCode == null ? Optional.empty() : catBreedRepository.findByCode(breedCode);
    }

    @Transactional(readOnly = true)
    public List<CatBreed> listActiveBreeds() {
        return catBreedRepository.findActive();
    }

    private Cat requireOwnedCat(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .filter(cat -> !cat.isDeleted())
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private Cat requireOwnedCatIncludingDeleted(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    private void assertWithinLimits(UUID ownerId, long currentCount) {
        Optional<CatProfileLimit> planLimit = catProfileLimitPort.limitFor(ownerId);
        if (planLimit.isPresent() && currentCount >= planLimit.get().max()) {
            throw new ConflictException(
                    CatErrorCode.CAT_PROFILE_LIMIT_REACHED,
                    planLimit.get().max(),
                    currentCount + 1,
                    planLimit.get().requiredPackageCode() == null ? "SUPPORT" : planLimit.get().requiredPackageCode());
        }
        int hardCap = appSettingPort.findInt(HARD_CAP_SETTING_KEY, ownerId).orElse(DEFAULT_HARD_CAP);
        if (currentCount >= hardCap) {
            throw new ConflictException(CatErrorCode.CAT_HARD_LIMIT_REACHED, hardCap);
        }
    }

    private void requireKnownBreed(String breedCode) {
        catBreedRepository.findByCode(breedCode)
                .filter(CatBreed::isActive)
                .orElseThrow(() -> new BusinessRuleException(CatErrorCode.CAT_BREED_UNKNOWN, breedCode));
    }

    private void requireSortable(String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        if (!SORTABLE_FIELDS.contains(sort)) {
            throw new BusinessRuleException(CatErrorCode.SORT_FIELD_NOT_ALLOWED, SORTABLE_FIELDS);
        }
    }

    private String newPublicCode() {
        for (int attempt = 0; attempt < MAX_PUBLIC_CODE_ATTEMPTS; attempt++) {
            String candidate = Cat.newPublicCode(publicCodeEntropy);
            if (!catRepository.existsByPublicCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Sinh publicCode thất bại sau " + MAX_PUBLIC_CODE_ATTEMPTS + " lần thử");
    }

    private Instant now() {
        return clock.instant();
    }


    public record CreateCatCommand(
            String name,
            LocalDate birthDate,
            Integer approxAgeMonths,
            String breedCode,
            String breedOther,
            String coatColor,
            CatSex sex,
            Boolean neutered,
            BigDecimal weightKg,
            boolean isPrimary,
            String notes) {
    }

    public record PatchCatCommand(
            String name,
            boolean nameProvided,
            LocalDate birthDate,
            Integer approxAgeMonths,
            String breedCode,
            String breedOther,
            String coatColor,
            CatSex sex,
            Boolean neutered,
            BigDecimal weightKg,
            String notes) {

        boolean touchesAge() {
            return birthDate != null || approxAgeMonths != null;
        }

        boolean hasName() {
            return nameProvided;
        }

        boolean hasAnyProfileField() {
            return breedOther != null || coatColor != null || sex != null || neutered != null
                    || notes != null;
        }
    }

    public record PrimaryCatResult(UUID catId, UUID previousPrimaryCatId) {
    }

    private static final class SecureRandomHolder {
        private static final SecureRandom RANDOM = new SecureRandom();

        private SecureRandomHolder() {
        }
    }
}
