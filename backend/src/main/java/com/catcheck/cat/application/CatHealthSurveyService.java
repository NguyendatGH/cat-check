package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.OnboardingMilestone;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.CatHealthSurvey;
import com.catcheck.cat.domain.port.CatHealthSurveyRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Khảo sát sức khoẻ nền 5 câu — D14/D15 của p8 §8.4.4.
 *
 * <p>Bản ghi là bất biến sau khi gửi (p4 C3): mỗi lần nộp tạo MỘT dòng mới, không có UPDATE.
 * "Nộp/cập nhật" trong tên gọi D14 nghĩa là "bản mới nhất có hiệu lực", không phải sửa bản cũ.</p>
 */
@Service
public class CatHealthSurveyService {

    /**
     * Bộ câu hỏi hiện hành. Chưa có bảng {@code health_survey_questionnaire} version hoá thật
     * (không nằm trong V7/V8) nên chốt một hằng số duy nhất — khớp giá trị FE đã hardcode
     * ({@code features/cat/hooks.ts#useSubmitHealthSurvey}: {@code questionnaireVersion: "v1"}).
     */
    public static final String CURRENT_QUESTIONNAIRE_VERSION = "v1";

    private final CatHealthSurveyRepository surveyRepository;
    private final CatRepository catRepository;
    private final UserAccountPort userAccountPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public CatHealthSurveyService(
            CatHealthSurveyRepository surveyRepository,
            CatRepository catRepository,
            UserAccountPort userAccountPort,
            UuidV7 uuidV7,
            Clock clock) {
        this.surveyRepository = surveyRepository;
        this.catRepository = catRepository;
        this.userAccountPort = userAccountPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** D15 — bản khảo sát mới nhất, {@code 404 SURVEY_NOT_FOUND} nếu chưa từng khảo sát. */
    @Transactional(readOnly = true)
    public CatHealthSurvey latest(UUID ownerId, UUID catId) {
        requireOwnedCat(ownerId, catId);
        return surveyRepository.findLatestByCatId(catId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.SURVEY_NOT_FOUND));
    }

    /**
     * D14 — nộp hoặc bỏ qua khảo sát.
     *
     * <p>{@code answers} truyền thẳng vào JSONB, KHÔNG re-validate cấu trúc từng câu ở tầng Java:
     * p4 C3 chốt việc đó là JSON Schema theo {@code questionnaire_version}, chưa có thư viện
     * JSON Schema nào được pin trong {@code research-integrations.md} cho phạm vi này — việc thêm
     * là quyết định của W3/A3 sau, không tự ý thêm dependency mới (pom.xml đã khoá).</p>
     */
    @Transactional
    public CatHealthSurvey submit(
            UUID ownerId, UUID catId, String questionnaireVersion, Map<String, Object> answers, boolean skipped) {
        requireWriteAllowed(ownerId);
        requireOwnedCat(ownerId, catId);
        String version = questionnaireVersion == null || questionnaireVersion.isBlank()
                ? CURRENT_QUESTIONNAIRE_VERSION : questionnaireVersion;
        if (!CURRENT_QUESTIONNAIRE_VERSION.equals(version)) {
            throw new BusinessRuleException(
                    CatErrorCode.SURVEY_VERSION_UNSUPPORTED, CURRENT_QUESTIONNAIRE_VERSION);
        }
        Instant now = clock.instant();
        UUID id = uuidV7.generate();
        CatHealthSurvey survey = skipped
                ? CatHealthSurvey.skipped(id, catId, version, ownerId, now)
                : CatHealthSurvey.submitted(id, catId, version, answers == null ? Map.of() : answers, ownerId, now);
        CatHealthSurvey saved = surveyRepository.save(survey);
        // Cot moc thu hai — "DONE_OR_SKIPPED" nen ca hai nhanh deu day tien (p4 §4.4).
        userAccountPort.advanceOnboardingStatus(ownerId, OnboardingMilestone.SURVEY_DONE_OR_SKIPPED);
        return saved;
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
