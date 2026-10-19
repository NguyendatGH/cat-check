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

@Service
public class HealthSurveyDefinitionService {

    private static final Pattern SAFE_VERSION = Pattern.compile("[a-z0-9.\\-]{1,16}");

    private final JsonMapper jsonMapper;
    private final Map<String, HealthSurveyDefinitionResponse> cache = new ConcurrentHashMap<>();

    public HealthSurveyDefinitionService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

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
            throw new UncheckedIOException("Khong doc duoc dinh nghia khao sat " + version, ex);
        }
    }
}
