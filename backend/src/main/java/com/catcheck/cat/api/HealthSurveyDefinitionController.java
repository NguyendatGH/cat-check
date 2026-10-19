package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.HealthSurveyDefinitionResponse;
import com.catcheck.cat.application.HealthSurveyDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cat", description = "Hồ sơ mèo và danh mục liên quan")
public class HealthSurveyDefinitionController {

    private final HealthSurveyDefinitionService definitionService;

    public HealthSurveyDefinitionController(HealthSurveyDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @Operation(
            operationId = "getHealthSurveyDefinition",
            summary = "F6 — Định nghĩa bộ câu hỏi khảo sát theo version",
            description = "Cần phiên đăng nhập. `labelKey` là khoá i18n, client tự resolve. "
                    + "Version không tồn tại → 422 `SURVEY_VERSION_UNSUPPORTED`.")
    @GetMapping("/reference/health-survey/{version}")
    public HealthSurveyDefinitionResponse getDefinition(@PathVariable String version) {
        return definitionService.findByVersion(version);
    }
}
