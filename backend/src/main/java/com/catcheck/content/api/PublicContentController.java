package com.catcheck.content.api;

import com.catcheck.content.api.dto.CareTipDetailResponse;
import com.catcheck.content.api.dto.CareTipSummaryResponse;
import com.catcheck.content.api.dto.PageResponse;
import com.catcheck.content.application.CareTipQueryService;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.shared.error.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Nội dung chăm sóc công khai — F7, F8. Không cần đăng nhập.
 *
 * <p>Locale lấy từ header {@code Accept-Language} qua {@code LocaleResolver} của
 * {@code shared.i18n.LocaleConfig} ({@code catcheck.i18n.default-locale=vi} khi client không gửi
 * header) — p8 §8.1.7. Controller KHÔNG tự đọc header, để một chỗ duy nhất quyết định ngôn ngữ.</p>
 */
@RestController
@RequestMapping("/api/v1/care-tips")
@Tag(name = "Nội dung chăm sóc", description = "F7, F8 — đọc nội dung đã công bố")
public class PublicContentController {

    /** p8 §8.1.4: {@code limit} mặc định 20, tối đa 100. */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final CareTipQueryService careTipQueryService;

    public PublicContentController(CareTipQueryService careTipQueryService) {
        this.careTipQueryService = careTipQueryService;
    }

    @Operation(
            operationId = "listCareTips",
            summary = "Danh sách nội dung đã công bố",
            description = "F7. Lọc bằng `category` và `kind`. Trả về envelope không phân trang vì "
                    + "số bài đã công bố là hữu hạn và nhỏ.")
    @GetMapping
    public PageResponse<CareTipSummaryResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            Locale locale) {

        // p8 §8.1.4: vượt trần trả 400 VALIDATION_FAILED, KHÔNG âm thầm kẹp xuống — kẹp âm thầm làm
        // client tưởng đã lấy hết dữ liệu rồi hiển thị thiếu bài.
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessRuleException(
                    ContentErrorCode.VALIDATION_FAILED, "limit phải nằm trong 1.." + MAX_LIMIT);
        }
        // R4: kiểu enum của domain không được nằm trong chữ ký controller, nên chuyển ở đây.
        CareTipCategory categoryFilter = EnumParam.parse(CareTipCategory.class, category, "category");
        CareTipKind kindFilter = EnumParam.parse(CareTipKind.class, kind, "kind");
        List<CareTipSummaryResponse> items = careTipQueryService
                .listPublished(locale.getLanguage(), categoryFilter, kindFilter, limit)
                .stream()
                .map(CareTipSummaryResponse::from)
                .toList();
        return PageResponse.of(items, limit);
    }

    @Operation(
            operationId = "getCareTipBySlug",
            summary = "Chi tiết một bài theo slug",
            description = "F8. `slug` là ngoại lệ duy nhất của p8 §8.1.3 (path param không phải id) "
                    + "vì slug là một phần của SEO và deep link.")
    @GetMapping("/{slug}")
    public CareTipDetailResponse getBySlug(@PathVariable String slug, Locale locale) {
        return CareTipDetailResponse.from(careTipQueryService.getPublishedBySlug(slug, locale.getLanguage()));
    }
}
