package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.HealthSurveyDefinitionResponse;
import com.catcheck.cat.application.HealthSurveyDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F6 — {@code GET /reference/health-survey/{version}} (p8 §8.4.6).
 *
 * <p>Controller RIÊNG thay vì thêm method vào {@link CatController}: endpoint này không thao tác
 * trên một con mèo cụ thể (không có {@code catId}, không kiểm quyền sở hữu) — nó là danh mục cấu
 * hình, chỉ tình cờ thuộc miền {@code cat} vì bộ câu hỏi gắn với {@code cat_health_survey}.</p>
 *
 * <p><b>Auth {@code U}</b> (p8 §8.4.6): khác 4 endpoint {@code /reference/*} còn lại vốn công
 * khai. Quy tắc đã có sẵn trong {@code shared/config/SecurityConfig} — matcher
 * {@code /api/v1/reference/health-survey/**} đặt TRƯỚC {@code /api/v1/reference/**} permitAll —
 * nên ở đây không cần annotation phân quyền nào.</p>
 *
 * <p>KHÔNG đặt {@code Cache-Control: public}: nội dung tuy tĩnh nhưng endpoint cần phiên, để
 * {@code public} là mời shared cache lưu phản hồi của một người dùng đã đăng nhập.</p>
 */
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
