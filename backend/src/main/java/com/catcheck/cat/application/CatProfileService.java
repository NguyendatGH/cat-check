package com.catcheck.cat.application;

import com.catcheck.cat.api.CatArchivedEvent;
import com.catcheck.cat.api.CatCreatedEvent;
import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.api.CatUpdatedEvent;
import com.catcheck.cat.domain.Cat;
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

/**
 * Nghiệp vụ hồ sơ mèo — D1 đến D8 của p8 §8.4.4.
 *
 * <p><b>Không có thao tác nào chạm tới bảng {@code app_user}.</b> Mọi kiểm tra "tài khoản này có được
 * ghi không" đi qua {@link UserAccountPort}, vì I19 (p4 §4.4.9) bắt buộc chỗ quyết định đó tồn tại
 * đúng một lần và nó thuộc module identity.</p>
 *
 * <p><b>Sự kiện phát ở đây, không phát ở controller.</b> Controller chỉ nên làm adapter HTTP; nếu
 * controller phát sự kiện thì một lời gọi service từ job nền hoặc từ test sẽ không sinh sự kiện, và
 * người đọc code không nhìn thấy được module cat tạo ra thứ gì.</p>
 */
@Service
public class CatProfileService {

    /** C31: trần cứng toàn hệ thống, đọc từ {@code app_setting} để đổi được mà không cần deploy. */
    public static final String HARD_CAP_SETTING_KEY = "cat.max_per_user";

    /** Giá trị khi khoá cấu hình chưa tồn tại hoặc không đọc được — phải khớp DEFAULT của V7. */
    static final int DEFAULT_HARD_CAP = 8;

    /**
     * Whitelist trường sắp xếp của D1 (p8 §8.5.3).
     *
     * <p>Không có {@code lastScanAt} dù p8 liệt kê nó: cột đó nằm ở module scan (A5) và chưa có cổng
     * đọc. Khi A5 có cổng, thêm vào đây cùng lúc với {@code ScanSummaryPort}.</p>
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "createdAt");

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

    // ============================================================== D1 — danh sách

    /**
     * D1 — danh sách mèo của người gọi.
     *
     * <p>Mặc định {@code status = ACTIVE} và sắp xếp {@code isPrimary desc, name asc}: mèo chính lên
     * đầu để màn hình chọn mèo (p9) không cần thao tác gì sau khi vào app.</p>
     *
     * <p>Không phân trang (C31) vì trần cứng là 8 hồ sơ — trả về trần cứng thì client vẫn xử lý được
     * mà không phải viết vòng lặp phân trang cho một danh sách không bao giờ dài hơn 8 dòng.</p>
     *
     * @param status {@code ACTIVE} hoặc {@code ARCHIVED}; {@code null} coi như {@code ACTIVE}
     * @param sort   trường sắp xếp; rỗng thì dùng mặc định. Trường ngoài whitelist ⇒
     *               {@code 400 SORT_FIELD_NOT_ALLOWED} chứ không âm thầm bỏ qua, để client biết là
     *               mình sai chứ không phải server bỏ sót
     */
    @Transactional(readOnly = true)
    public List<Cat> listForOwner(UUID ownerId, CatStatus status, String sort) {
        CatStatus effectiveStatus = status == null ? CatStatus.ACTIVE : status;
        requireSortable(sort);
        return catRepository.findAllByOwnerId(ownerId, effectiveStatus, false);
    }

    // ============================================================== D2 — tạo hồ sơ

    /**
     * D2 — tạo hồ sơ mèo.
     *
     * <p>Thứ tự kiểm là bắt buộc, không phải tuỳ chọn:</p>
     * <ol>
     *   <li>{@code U!} — tài khoản {@code RESTRICTED} thì chặn mọi ghi;</li>
     *   <li>entitlement gói — vượt thì CTA "nâng gói";</li>
     *   <li>trần cứng — vượt thì CTA "liên hệ hỗ trợ".</li>
     * </ol>
     *
     * <p><b>Mèo đầu tiên luôn là mèo chính</b> bất kể client gửi gì: nếu không, tài khoản mới có bé mà
     * mọi màn hình đều cần chọn mèo thì người dùng phải tự tìm chức năng "đặt bé mặc định".</p>
     */
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
        // Mèo đầu tiên tự làm chính; các mèo sau thì theo ý client.
        boolean wantsPrimary = isFirstCat || command.isPrimary();

        // Bỏ cờ mèo chính cũ TRƯỚC khi lưu mèo mới: partial unique index uq_cat_owner_primary sẽ
        // từ chối ghi nếu hai hồ sơ cùng lúc có is_primary = true.
        UUID previousPrimary = wantsPrimary
                ? catRepository.findPrimaryByOwnerId(ownerId).map(Cat::getId).orElse(null)
                : null;
        if (wantsPrimary) {
            catRepository.clearPrimaryForOwner(ownerId, now);
        }

        Cat cat = Cat.create(
                uuidV7.generate(),
                ownerId,
                command.name(),
                command.birthDate(),
                command.approxAgeMonths(),
                newPublicCode(),
                LocalDate.now(clock),
                now);
        cat.applyProfile(command.breedCode(), command.breedOther(), command.coatColor(),
                command.sex(), command.neutered(), command.weightKg(), command.notes(),
                LocalDate.now(clock), now);
        if (wantsPrimary) {
            cat.makePrimary(now);
        }

        Cat saved = catRepository.save(cat);
        // Cot moc onboarding dau tien (p4 §4.4 `app_user.onboarding_status`). Goi thang qua SPI
        // chu khong dua vao CatCreatedEvent ben duoi: event do KHONG co listener nao va identity
        // khong duoc phep phu thuoc cat — xem javadoc UserAccountPort#advanceOnboardingStatus.
        userAccountPort.advanceOnboardingStatus(ownerId, OnboardingMilestone.CAT_CREATED);
        eventPublisher.publishEvent(new CatCreatedEvent(
                saved.getId(), ownerId, saved.getPublicCode(), isFirstCat, wantsPrimary, now));
        return saved;
    }

    // ============================================================== D3 — chi tiết

    /** D3 — chi tiết một hồ sơ. Truy vấn đã lọc theo chủ nên hồ sơ người khác trả 404. */
    @Transactional(readOnly = true)
    public Cat get(UUID ownerId, UUID catId) {
        return requireOwnedCat(ownerId, catId);
    }

    // ============================================================== D4 — sửa

    /**
     * D4 — merge-patch.
     *
     * <p>Cố ý KHÔNG cho sửa {@code isPrimary}: D8 là endpoint riêng với kiểm tra riêng, và cho sửa
     * qua PATCH sẽ lách qua {@code CAT_ALREADY_PRIMARY} và bỏ qua việc hạ cờ mèo chính cũ.</p>
     */
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

        Cat saved = catRepository.save(cat);
        eventPublisher.publishEvent(new CatUpdatedEvent(
                saved.getId(), ownerId, changed, null, now()));
        return saved;
    }

    // ============================================================== D5 — xoá mềm

    /** D5 — xoá mềm. Xoá lần hai trả {@code 409 CAT_ALREADY_DELETED} thay vì trả 204 âm thầm. */
    @Transactional
    public void softDelete(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
        if (cat.isDeleted()) {
            throw new ConflictException(CatErrorCode.CAT_ALREADY_DELETED);
        }
        Instant now = now();
        cat.softDelete(now);
        catRepository.save(cat);
        eventPublisher.publishEvent(new CatArchivedEvent(catId, ownerId, true, now));
    }

    // ============================================================== D6/D7 — lưu trữ

    /** D6 — "bé đã mất / đã cho đi". Dữ liệu và biểu đồ vẫn xem được. */
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

    /**
     * D7 — đưa trở lại theo dõi.
     *
     * <p>Cố ý idempotent: p8 không định nghĩa mã lỗi cho "đang theo dõi mà lại bỏ lưu trữ", và tự
     * chế ra một mã không có trong danh mục §8.2.4 sẽ phá hợp đồng ổn định của {@code code}. Gọi
     * {@code unarchive} hai lần trả {@code 200} với cùng hồ sơ — vô hại và dễ retry.</p>
     */
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

    // ============================================================== D8 — mèo chính

    /**
     * D8 — đặt mèo chính.
     *
     * <p>Trả về cả mèo chính cũ để client có thể đổi nhãn "bé mặc định" ngay mà không phải gọi lại
     * danh sách.</p>
     */
    @Transactional
    public PrimaryCatResult setPrimary(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
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
        // Hạ cờ cũ trước: index một phần chỉ cho phép tối đa một hàng is_primary = true mỗi chủ.
        catRepository.clearPrimaryForOwner(ownerId, now);
        cat.makePrimary(now);
        catRepository.save(cat);
        eventPublisher.publishEvent(new CatUpdatedEvent(
                catId, ownerId, Set.of("isPrimary"), null, now));
        return new PrimaryCatResult(catId, previousPrimary);
    }

    // ============================================================= dùng chung

    /**
     * Tra cứu một mèo kèm tên giống, dùng cho D3.
     *
     * @return tên giống theo locale, hoặc {@code null} nếu hồ sơ không chọn giống
     */
    @Transactional(readOnly = true)
    public Optional<CatBreed> findBreed(String breedCode) {
        return breedCode == null ? Optional.empty() : catBreedRepository.findByCode(breedCode);
    }

    /** F2 — {@code GET /reference/cat-breeds}: danh mục giống đang {@code active}, theo sort_order. */
    @Transactional(readOnly = true)
    public List<CatBreed> listActiveBreeds() {
        return catBreedRepository.findActive();
    }

    private Cat requireOwnedCat(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    /**
     * {@code U!} của p8 §8.3.2: tài khoản {@code RESTRICTED} bị chặn mọi thao tác ghi.
     *
     * <p>Các trạng thái khác đã bị chặn ở tầng phiên (chưa verify, bị khoá, đang ân hạn xoá) nên
     * không tới được đây. Tài khoản không tồn tại thì cũng không tới được — nhưng adapter vẫn trả
     * {@code null} cho trường hợp dữ liệu bị xoá cứng, và ta chặn chứ không cho qua.</p>
     */
    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    /**
     * C31 — hai lớp hạn mức, kiểm theo đúng thứ tự.
     *
     * <p>Thứ tự quyết định câu CTA: vượt entitlement là lỗi của người dùng (mua thêm được), vượt
     * trần cứng thì không (phải liên hệ hỗ trợ). Kiểm ngược thứ tự sẽ bắt người dùng nâng gói trong
     * khi nâng gói cũng không giúp ích gì.</p>
     */
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

    /**
     * Sinh mã hiển thị chưa từng dùng.
     *
     * <p>Mã nằm trong bản in bảng tên mèo mà người dùng gõ tay, nên va chạm là thật (khoảng 32^6 ≈
     * 10^9 mã, mỗi lần thử lại vô hạn là đủ). Thử tối đa 8 lần rồi mới ném lỗi hệ thống — nếu hết
     * cả 8 lần thì đó là sự cố thật, không phải xui xẻo hiếm.</p>
     */
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

    // ------------------------------------------------------------------ lệnh vào

    /**
     * Lệnh tạo hồ sơ (D2).
     *
     * <p>Kiểu tuổi là {@code birthDate} HOẶC {@code approxAgeMonths}; gửi cả hai hoặc không gửi cái
     * nào thì domain ném {@code CAT_AGE_CONFLICT}.</p>
     */
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

    /**
     * Lệnh sửa hồ sơ (D4), theo ngữ nghĩa merge-patch.
     *
     * <p>Khác biệt quan trọng giữa "không gửi trường" và "gửi {@code null}": với {@code birthDate}
     * và {@code approxAgeMonths}, gửi {@code null} nghĩa là KHÔNG chạm vào tuổi. Muốn xoá cả hai
     * nguồn thì phải dùng một giá trị khác — và p8 cũng không cung cấp cách nào làm việc đó, vì bản
     * ghi hồ sơ luôn cần một cách nhập tuổi.</p>
     */
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

    /** Kết quả D8: mèo mới làm chính và mèo chính trước đó. */
    public record PrimaryCatResult(UUID catId, UUID previousPrimaryCatId) {
    }

    /**
     * Một {@link SecureRandom} dùng chung cho cả ứng dụng.
     *
     * <p>Giữ ở đây thay vì tạo mới mỗi lần gọi: khởi tạo {@code SecureRandom} trên Linux đọc
     * {@code /dev/urandom} và có thể chặn, nên làm việc đó trong constructor là việc một lần duy
     * nhất, không phải việc của mỗi request.</p>
     */
    private static final class SecureRandomHolder {
        private static final SecureRandom RANDOM = new SecureRandom();

        private SecureRandomHolder() {
        }
    }
}
