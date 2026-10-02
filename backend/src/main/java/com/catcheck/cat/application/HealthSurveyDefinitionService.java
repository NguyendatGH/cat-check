package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.api.dto.HealthSurveyDefinitionResponse;
import com.catcheck.shared.error.BusinessRuleException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * F6 — nạp định nghĩa bộ câu hỏi khảo sát theo {@code questionnaire_version} (p8 §8.4.6, p4 C3).
 *
 * <p>Định nghĩa nằm ở file đi kèm build {@code resources/questionnaire/health-survey-{version}.json}
 * chứ không phải bảng DB — p4 C3 chốt như vậy. Bản cũ PHẢI đọc được mãi: câu trả lời đã lưu
 * trong {@code cat_health_survey.answers} chỉ giải nghĩa được theo đúng bộ câu hỏi của nó, nên
 * thêm version mới là THÊM file, không sửa file cũ.</p>
 *
 * <p>Cache in-memory: file bất biến trong một lần chạy, không cần đọc đĩa mỗi request.</p>
 */
@Service
public class HealthSurveyDefinitionService {

    /** Chặn path traversal: version chỉ gồm chữ/số/dấu chấm/gạch, ví dụ {@code v1}, {@code v2.1}. */
    private static final Pattern SAFE_VERSION = Pattern.compile("[a-z0-9.\\-]{1,16}");

    private final JsonMapper jsonMapper;
    private final Map<String, HealthSurveyDefinitionResponse> cache = new ConcurrentHashMap<>();

    public HealthSurveyDefinitionService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @throws BusinessRuleException {@code SURVEY_VERSION_UNSUPPORTED} nếu không có file cho
     *         version đó (gồm cả version sai định dạng)
     */
    public HealthSurveyDefinitionResponse findByVersion(String version) {
        String normalized = version == null ? "" : version.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SAFE_VERSION.matcher(normalized).matches()) {
            throw new BusinessRuleException(CatErrorCode.SURVEY_VERSION_UNSUPPORTED, version);
        }
        HealthSurveyDefinitionResponse cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }
        HealthSurveyDefinitionResponse loaded = load(normalized);
        cache.put(normalized, loaded);
        return loaded;
    }

    private HealthSurveyDefinitionResponse load(String version) {
        ClassPathResource resource = new ClassPathResource("questionnaire/health-survey-" + version + ".json");
        if (!resource.exists()) {
            throw new BusinessRuleException(CatErrorCode.SURVEY_VERSION_UNSUPPORTED, version);
        }
        try (InputStream in = resource.getInputStream()) {
            return jsonMapper.readValue(in, HealthSurveyDefinitionResponse.class);
        } catch (IOException ex) {
            // File nằm trong jar và đã exists() — hỏng ở đây là lỗi build/đóng gói, không phải
            // đầu vào người dùng, nên để nổi lên 500 thay vì nuốt thành 422.
            throw new UncheckedIOException("Khong doc duoc dinh nghia khao sat " + version, ex);
        }
    }
}
