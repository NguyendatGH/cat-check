package com.catcheck.cat.application;

import com.catcheck.cat.api.CatContextQuery;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatClinicalSignReport;
import com.catcheck.cat.domain.CatHealthSurvey;
import com.catcheck.cat.domain.CatSex;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.ClinicalSign;
import com.catcheck.cat.domain.ClinicalSignSource;
import com.catcheck.cat.domain.port.CatHealthSurveyRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.cat.domain.port.ClinicalSignReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1 ({@code docs/cat-rag-credit-pipeline.md} mục A4-(1)) — kiểm {@code CatContextQuery}: điểm
 * kiểm sở hữu duy nhất cho ngữ cảnh trợ lý AI, cộng ánh xạ hồ sơ/khảo sát/dấu hiệu lâm sàng.
 */
class CatContextQueryAdapterTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000000f");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");

    private FakeCatRepository catRepository;
    private FakeSurveyRepository surveyRepository;
    private FakeSignReportRepository signReportRepository;
    private CatContextQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        catRepository = new FakeCatRepository();
        surveyRepository = new FakeSurveyRepository();
        signReportRepository = new FakeSignReportRepository();
        adapter = new CatContextQueryAdapter(
                catRepository, surveyRepository, signReportRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Mèo của người khác ⇒ Optional.empty(), không tiết lộ mèo tồn tại")
    void catOwnedByAnotherUserIsInvisible() {
        catRepository.rows.add(cat());

        Optional<CatContextQuery.CatProfileSnapshot> result = adapter.findSnapshot(OTHER_USER_ID, CAT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Mèo không tồn tại ⇒ Optional.empty() giống hệt trường hợp sai chủ")
    void unknownCatIsInvisible() {
        Optional<CatContextQuery.CatProfileSnapshot> result = adapter.findSnapshot(OWNER_ID, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Mèo chưa khảo sát, chưa khai dấu hiệu lâm sàng ⇒ snapshot vẫn dựng được, không NPE")
    void snapshotWithoutSurveyOrClinicalSignsDoesNotThrow() {
        catRepository.rows.add(cat());

        CatContextQuery.CatProfileSnapshot snapshot = adapter.findSnapshot(OWNER_ID, CAT_ID).orElseThrow();

        assertThat(snapshot.catId()).isEqualTo(CAT_ID);
        assertThat(snapshot.name()).isEqualTo("Mun");
        assertThat(snapshot.survey()).isNull();
        assertThat(snapshot.recentClinicalSigns()).isEmpty();
    }

    @Test
    @DisplayName("Khảo sát bị bỏ qua (skipped) ⇒ survey = null, không trả bản ghi rỗng")
    void skippedSurveyIsTreatedAsAbsent() {
        catRepository.rows.add(cat());
        surveyRepository.latest = CatHealthSurvey.skipped(UUID.randomUUID(), CAT_ID, "v1", OWNER_ID, NOW);

        CatContextQuery.CatProfileSnapshot snapshot = adapter.findSnapshot(OWNER_ID, CAT_ID).orElseThrow();

        assertThat(snapshot.survey()).isNull();
    }

    @Test
    @DisplayName("Khảo sát đã gửi ⇒ map đúng 5 câu trả lời chính, mã thô không dịch nghĩa")
    void submittedSurveyMapsRawAnswerCodes() {
        catRepository.rows.add(cat());
        Map<String, Object> answers = Map.of(
                "litterType", "CATCHECK_SMART",
                "urinaryHistory", "NONE",
                "dietType", "DRY",
                "urinationFrequency", "1_2_PER_DAY",
                "symptoms", List.of("STRAINING", "CRYING"));
        surveyRepository.latest = CatHealthSurvey.submitted(UUID.randomUUID(), CAT_ID, "v1", answers, OWNER_ID, NOW);

        CatContextQuery.SurveyAnswers survey = adapter.findSnapshot(OWNER_ID, CAT_ID).orElseThrow().survey();

        assertThat(survey).isNotNull();
        assertThat(survey.questionnaireVersion()).isEqualTo("v1");
        assertThat(survey.litterType()).isEqualTo("CATCHECK_SMART");
        assertThat(survey.urinaryHistory()).isEqualTo("NONE");
        assertThat(survey.dietType()).isEqualTo("DRY");
        assertThat(survey.urinationFrequency()).isEqualTo("1_2_PER_DAY");
        assertThat(survey.symptoms()).containsExactly("STRAINING", "CRYING");
    }

    @Test
    @DisplayName("Dấu hiệu lâm sàng: chỉ 14 ngày gần nhất lọt qua, bản khai quá cũ bị loại")
    void onlyClinicalSignsWithinFourteenDaysAreIncluded() {
        catRepository.rows.add(cat());
        signReportRepository.rows.add(signReport(NOW.minusSeconds(3600), Set.of(ClinicalSign.STRAINING), false));
        signReportRepository.rows.add(signReport(NOW.minus(java.time.Duration.ofDays(20)),
                Set.of(ClinicalSign.CRYING), true));

        List<CatContextQuery.ClinicalSignEntry> signs =
                adapter.findSnapshot(OWNER_ID, CAT_ID).orElseThrow().recentClinicalSigns();

        assertThat(signs).hasSize(1);
        assertThat(signs.getFirst().signs()).containsExactly("STRAINING");
        assertThat(signs.getFirst().acknowledged()).isFalse();
    }

    @Test
    @DisplayName("Tuổi tính từ birthDate theo đúng ngày của Clock đã inject")
    void ageIsComputedFromInjectedClock() {
        catRepository.rows.add(Cat.create(
                CAT_ID, OWNER_ID, "Mun", LocalDate.of(2025, 10, 9), null,
                "CC-VN-ABC123", LocalDate.of(2025, 10, 9), NOW));

        CatContextQuery.CatProfileSnapshot snapshot = adapter.findSnapshot(OWNER_ID, CAT_ID).orElseThrow();

        assertThat(snapshot.ageMonths()).isEqualTo(12);
    }

    private static Cat cat() {
        Cat cat = Cat.create(CAT_ID, OWNER_ID, "Mun", null, 12, "CC-VN-ABC123", LocalDate.of(2026, 10, 1), NOW);
        cat.applyProfile("OTHER", null, null, CatSex.FEMALE, true, new BigDecimal("3.50"), null,
                LocalDate.of(2026, 10, 1), NOW);
        return cat;
    }

    private static CatClinicalSignReport signReport(Instant reportedAt, Set<ClinicalSign> signs, boolean acknowledged) {
        CatClinicalSignReport report = CatClinicalSignReport.create(
                UUID.randomUUID(), CAT_ID, OWNER_ID, signs, ClinicalSignSource.RESULT_SCREEN, true, reportedAt);
        if (acknowledged) {
            report.acknowledge(reportedAt.plusSeconds(60));
        }
        return report;
    }

    private static final class FakeCatRepository implements CatRepository {

        private final List<Cat> rows = new ArrayList<>();

        @Override
        public Optional<Cat> findByIdAndOwnerId(UUID id, UUID ownerId) {
            return rows.stream()
                    .filter(cat -> cat.getId().equals(id) && cat.getOwnerId().equals(ownerId))
                    .findFirst();
        }

        @Override
        public List<Cat> findAllByOwnerId(UUID ownerId, CatStatus statusFilter, boolean includeArchived) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public long countActiveByOwnerId(UUID ownerId) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public Optional<Cat> findPrimaryByOwnerId(UUID ownerId) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public void clearPrimaryForOwner(UUID ownerId, Instant now) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public boolean existsByPublicCode(String publicCode) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public Cat save(Cat cat) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }
    }

    private static final class FakeSurveyRepository implements CatHealthSurveyRepository {

        private CatHealthSurvey latest;

        @Override
        public Optional<CatHealthSurvey> findLatestByCatId(UUID catId) {
            return Optional.ofNullable(latest);
        }

        @Override
        public List<CatHealthSurvey> findHistoryByCatId(UUID catId, int limit) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public boolean hasSubmittedByCatId(UUID catId) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public CatHealthSurvey save(CatHealthSurvey survey) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }
    }

    private static final class FakeSignReportRepository implements ClinicalSignReportRepository {

        private final List<CatClinicalSignReport> rows = new ArrayList<>();

        @Override
        public Optional<CatClinicalSignReport> findById(UUID id) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public List<CatClinicalSignReport> findByCatId(UUID catId, int limit) {
            return rows.stream()
                    .filter(report -> report.getCatId().equals(catId))
                    .sorted((a, b) -> b.getReportedAt().compareTo(a.getReportedAt()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countByCatId(UUID catId) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public List<CatClinicalSignReport> findUnacknowledged(int limit) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }

        @Override
        public CatClinicalSignReport save(CatClinicalSignReport report) {
            throw new UnsupportedOperationException("not used by CatContextQueryAdapter");
        }
    }
}
