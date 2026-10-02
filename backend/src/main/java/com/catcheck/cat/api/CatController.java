package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.AvatarUrlResponse;
import com.catcheck.cat.api.dto.CatBreedListResponse;
import com.catcheck.cat.api.dto.CatBreedResponse;
import com.catcheck.cat.api.dto.CatHealthSurveyResponse;
import com.catcheck.cat.api.dto.CatNoteResponse;
import com.catcheck.cat.api.dto.CatPageResponse;
import com.catcheck.cat.api.dto.CatResponse;
import com.catcheck.cat.api.dto.CatSummaryResponse;
import com.catcheck.cat.api.dto.CatTrendsResponse;
import com.catcheck.cat.api.dto.ClinicalSignReportResponse;
import com.catcheck.cat.api.dto.CreateCatRequest;
import com.catcheck.cat.api.dto.CreateNoteRequest;
import com.catcheck.cat.api.dto.PatchCatRequest;
import com.catcheck.cat.api.dto.PatchNoteRequest;
import com.catcheck.cat.api.dto.PrimaryCatResultDto;
import com.catcheck.cat.api.dto.ReportClinicalSignsRequest;
import com.catcheck.cat.api.dto.SubmitHealthSurveyRequest;
import com.catcheck.cat.application.CatAvatarService;
import com.catcheck.cat.application.CatHealthSurveyService;
import com.catcheck.cat.application.CatInsightService;
import com.catcheck.cat.application.CatNoteService;
import com.catcheck.cat.application.CatProfileService;
import com.catcheck.cat.application.ClinicalSignReportService;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatNote;
import com.catcheck.cat.domain.CatSex;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.NoteType;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Nhóm D — Hồ sơ mèo (20 endpoint, p8 §8.4.4) + F2 (giống mèo, cùng bảng {@code cat_breed}).
 *
 * <p><b>D12/D13 đã triển khai</b> (trước đây là stub 501 chờ module {@code scan}/{@code insight}).
 * Hai module đó nay đã có, nhưng {@code cat} vẫn KHÔNG được phép phụ thuộc chúng
 * ({@code cat/package-info.java}), nên dữ liệu quét/flag đọc qua {@code ScanInsightPort} — cổng
 * SPI riêng của cat, hiện thực bằng JDBC thô, không import type nào của hai module kia.</p>
 *
 * <p><b>D18/D19 không có {@code catId} trên path</b> ({@code /cat-notes/{id}}) nên nằm cùng
 * controller này thay vì tách controller riêng — cùng module, cùng bảng, không có lý do kiến trúc
 * nào để tách (giống cách {@code AccountController} của identity gộp {@code /users/me} lẫn
 * {@code /account/...}).</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cats", description = "Nhóm D — hồ sơ mèo, ghi chú, khảo sát sức khoẻ, dấu hiệu lâm sàng")
public class CatController {

    private static final int DEFAULT_NOTE_PAGE_SIZE = 20;

    private final CatProfileService catProfileService;
    private final CatAvatarService catAvatarService;
    private final CatNoteService catNoteService;
    private final CatHealthSurveyService catHealthSurveyService;
    private final ClinicalSignReportService clinicalSignReportService;
    private final CatInsightService catInsightService;
    private final CatResponseAssembler responseAssembler;

    public CatController(
            CatProfileService catProfileService,
            CatAvatarService catAvatarService,
            CatNoteService catNoteService,
            CatHealthSurveyService catHealthSurveyService,
            ClinicalSignReportService clinicalSignReportService,
            CatInsightService catInsightService,
            CatResponseAssembler responseAssembler) {
        this.catProfileService = catProfileService;
        this.catAvatarService = catAvatarService;
        this.catNoteService = catNoteService;
        this.catHealthSurveyService = catHealthSurveyService;
        this.clinicalSignReportService = clinicalSignReportService;
        this.catInsightService = catInsightService;
        this.responseAssembler = responseAssembler;
    }

    // ============================================================== D1/D2 — danh sách/tạo

    /**
     * D1 — danh sách mèo của tôi.
     *
     * <p>{@code sort} chỉ được VALIDATE (whitelist {@code name}/{@code createdAt} —
     * {@code CatProfileService}), thứ tự trả về THỰC TẾ luôn {@code isPrimary desc, name asc}:
     * {@code CatRepository.findAllByOwnerId} (port của A3) không nhận tham số sắp xếp động. Đây là
     * giới hạn có sẵn từ chữ ký port, không phải lỗi ở tầng glue HTTP này — xem
     * {@code docs/handovers/A3-backend-fix.md}.</p>
     */
    @Operation(operationId = "listCats", summary = "D1 — Danh sách mèo của tôi")
    @GetMapping("/cats")
    public CatPageResponse<CatResponse> listCats(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            Locale locale) {
        CatStatus statusFilter = EnumParam.parse(CatStatus.class, status, "status");
        List<Cat> cats = catProfileService.listForOwner(user.userId(), statusFilter, sort);
        List<CatResponse> items = cats.stream().map(cat -> responseAssembler.toResponse(cat, locale)).toList();
        return CatPageResponse.of(items, items.size(), false);
    }

    /** D2 — tạo hồ sơ mèo. */
    @Operation(operationId = "createCat", summary = "D2 — Tạo hồ sơ mèo")
    @PostMapping("/cats")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CatResponse> createCat(
            @CurrentUser SecurityPrincipal user,
            @Valid @RequestBody CreateCatRequest request,
            Locale locale) {
        CatSex sex = EnumParam.parse(CatSex.class, request.sex(), "sex");
        Cat saved = catProfileService.create(user.userId(), new CatProfileService.CreateCatCommand(
                request.name(),
                request.birthDate(),
                request.approxAgeMonths(),
                request.breedCode(),
                request.breedOther(),
                request.coatColor(),
                sex,
                request.neutered(),
                request.weightKg(),
                Boolean.TRUE.equals(request.isPrimary()),
                request.notes()));
        URI location = URI.create("/api/v1/cats/" + saved.getId());
        return ResponseEntity.created(location).body(responseAssembler.toResponse(saved, locale));
    }

    // ============================================================== D3/D4/D5 — chi tiết/sửa/xoá

    /** D3 — chi tiết hồ sơ mèo. */
    @Operation(operationId = "getCat", summary = "D3 — Chi tiết hồ sơ mèo")
    @GetMapping("/cats/{catId}")
    public CatResponse getCat(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID catId, Locale locale) {
        return responseAssembler.toResponse(catProfileService.get(user.userId(), catId), locale);
    }

    /** D4 — sửa hồ sơ (merge-patch). */
    @Operation(operationId = "patchCat", summary = "D4 — Sửa hồ sơ mèo (merge-patch)")
    @PatchMapping("/cats/{catId}")
    public CatResponse patchCat(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @Valid @RequestBody PatchCatRequest request,
            Locale locale) {
        CatSex sex = EnumParam.parse(CatSex.class, request.sex(), "sex");
        Cat saved = catProfileService.patch(user.userId(), catId, new CatProfileService.PatchCatCommand(
                request.name(),
                request.name() != null,
                request.birthDate(),
                request.approxAgeMonths(),
                request.breedCode(),
                request.breedOther(),
                request.coatColor(),
                sex,
                request.neutered(),
                request.weightKg(),
                request.notes()));
        return responseAssembler.toResponse(saved, locale);
    }

    /** D5 — xoá mềm hồ sơ. */
    @Operation(operationId = "deleteCat", summary = "D5 — Xoá mềm hồ sơ mèo")
    @DeleteMapping("/cats/{catId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCat(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId) {
        catProfileService.softDelete(user.userId(), catId);
    }

    // ============================================================== D6/D7 — lưu trữ

    /** D6 — "bé đã mất / đã cho đi" ⇒ {@code status = ARCHIVED}. */
    @Operation(operationId = "archiveCat", summary = "D6 — Lưu trữ hồ sơ mèo")
    @PostMapping("/cats/{catId}/archive")
    public CatResponse archiveCat(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId, Locale locale) {
        return responseAssembler.toResponse(catProfileService.archive(user.userId(), catId), locale);
    }

    /** D7 — đưa trở lại theo dõi. */
    @Operation(operationId = "unarchiveCat", summary = "D7 — Bỏ lưu trữ hồ sơ mèo")
    @PostMapping("/cats/{catId}/unarchive")
    public CatResponse unarchiveCat(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId, Locale locale) {
        return responseAssembler.toResponse(catProfileService.unarchive(user.userId(), catId), locale);
    }

    // ============================================================== D8 — mèo chính

    /** D8 — đặt mèo chính. */
    @Operation(operationId = "setPrimaryCat", summary = "D8 — Đặt mèo chính")
    @PutMapping("/cats/{catId}/primary")
    public PrimaryCatResultDto setPrimaryCat(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId) {
        CatProfileService.PrimaryCatResult result = catProfileService.setPrimary(user.userId(), catId);
        return new PrimaryCatResultDto(
                result.catId().toString(),
                result.previousPrimaryCatId() == null ? null : result.previousPrimaryCatId().toString());
    }

    // ============================================================== D9/D10/D11 — ảnh đại diện

    /** D9 — ảnh mèo (stream). */
    @Operation(operationId = "getCatAvatar", summary = "D9 — Ảnh đại diện mèo")
    @GetMapping("/cats/{catId}/avatar")
    public ResponseEntity<byte[]> getCatAvatar(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId) {
        CatAvatarService.AvatarContent content = catAvatarService.openAvatar(user.userId(), catId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.AVATAR_NOT_FOUND));
        byte[] bytes;
        try {
            bytes = StreamUtils.copyToByteArray(content.content());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType())).body(bytes);
    }

    /** D10 — tải ảnh mèo (multipart). */
    @Operation(operationId = "uploadCatAvatar", summary = "D10 — Tải ảnh đại diện mèo")
    @PutMapping("/cats/{catId}/avatar")
    public AvatarUrlResponse uploadCatAvatar(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @RequestParam("file") MultipartFile file) {
        try {
            CatAvatarService.AvatarResult result = catAvatarService.replaceAvatar(
                    user.userId(), catId, new CatAvatarService.AvatarUpload(
                            file.getContentType(), file.getInputStream(), file.getSize()));
            return new AvatarUrlResponse(result.avatarUrl().toString());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** D11 — gỡ ảnh mèo. */
    @Operation(operationId = "removeCatAvatar", summary = "D11 — Gỡ ảnh đại diện mèo")
    @DeleteMapping("/cats/{catId}/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCatAvatar(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId) {
        catAvatarService.removeAvatar(user.userId(), catId);
    }

    // ============================================================== D12/D13 — tổng quan & xu hướng

    /**
     * D12 — card tổng quan cho dashboard (p8 §8.9 dòng 1185).
     *
     * <p>Trước đây là stub 501 với lý do "chờ port từ scan/insight" — lý do đó đã cũ; hai module
     * đều đã có, nhưng chiều phụ thuộc {@code cat -> scan} vẫn không hợp lệ nên dữ liệu quét được
     * đọc qua {@code ScanInsightPort} (SPI riêng của cat, JDBC thô).</p>
     */
    @Operation(operationId = "getCatSummary", summary = "D12 — Card tổng quan")
    @GetMapping("/cats/{catId}/summary")
    public CatSummaryResponse getCatSummary(@CurrentUser SecurityPrincipal user, @PathVariable UUID catId) {
        return CatSummaryResponse.from(catInsightService.summary(user.userId(), catId));
    }

    /**
     * D13 — chuỗi pH + thống kê (p8 §8.9 dòng 1186). Cần entitlement {@code E:trend}, thiếu thì
     * {@code 403 FEATURE_NOT_IN_PLAN}.
     *
     * @param range {@code 7D|30D|90D|CUSTOM}, mặc định {@code 30D}; {@code CUSTOM} bắt buộc có
     *              cả {@code from} lẫn {@code to}
     */
    @Operation(operationId = "getCatTrends", summary = "D13 — Xu hướng pH")
    @GetMapping("/cats/{catId}/trends")
    public CatTrendsResponse getCatTrends(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return CatTrendsResponse.from(catInsightService.trends(user.userId(), catId, range, from, to));
    }

    // ============================================================== D14/D15 — khảo sát sức khoẻ

    /** D14 — nộp/bỏ qua khảo sát sức khoẻ. */
    @Operation(operationId = "submitHealthSurvey", summary = "D14 — Nộp khảo sát sức khoẻ")
    @PostMapping("/cats/{catId}/health-survey")
    @ResponseStatus(HttpStatus.CREATED)
    public CatHealthSurveyResponse submitHealthSurvey(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @RequestBody SubmitHealthSurveyRequest request) {
        return CatHealthSurveyResponse.from(catHealthSurveyService.submit(
                user.userId(), catId, request.questionnaireVersion(), request.answers(), request.skipped()));
    }

    /** D15 — bản khảo sát mới nhất. */
    @Operation(operationId = "getHealthSurvey", summary = "D15 — Bản khảo sát mới nhất")
    @GetMapping("/cats/{catId}/health-survey")
    public CatHealthSurveyResponse getHealthSurvey(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID catId) {
        return CatHealthSurveyResponse.from(catHealthSurveyService.latest(user.userId(), catId));
    }

    // ============================================================== D16/D17 — ghi chú (theo mèo)

    /** D16 — nhật ký ghi chú. */
    @Operation(operationId = "listCatNotes", summary = "D16 — Nhật ký ghi chú")
    @GetMapping("/cats/{catId}/notes")
    public CatPageResponse<CatNoteResponse> listCatNotes(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        NoteType typeFilter = EnumParam.parse(NoteType.class, type, "type");
        int pageIndex = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_NOTE_PAGE_SIZE : Math.max(size, 1);
        CatNoteService.NotePage result = catNoteService.listByCat(user.userId(), catId, typeFilter, pageIndex, pageSize);
        List<CatNoteResponse> items = result.items().stream().map(CatNoteResponse::from).toList();
        return CatPageResponse.of(items, pageSize, result.hasMore());
    }

    /** D17 — thêm ghi chú. */
    @Operation(operationId = "createCatNote", summary = "D17 — Thêm ghi chú")
    @PostMapping("/cats/{catId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public CatNoteResponse createCatNote(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @Valid @RequestBody CreateNoteRequest request) {
        NoteType noteType = EnumParam.parse(NoteType.class, request.noteType(), "noteType");
        CatNote note = catNoteService.create(user.userId(), catId, noteType, request.body(), request.occurredOn());
        return CatNoteResponse.from(note);
    }

    // ============================================================== D18/D19 — ghi chú (theo id)

    /** D18 — sửa ghi chú của chính mình (merge-patch). Path KHÔNG mang {@code catId} (p8 §8.4.4). */
    @Operation(operationId = "patchCatNote", summary = "D18 — Sửa ghi chú")
    @PatchMapping("/cat-notes/{noteId}")
    public CatNoteResponse patchCatNote(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID noteId,
            @Valid @RequestBody PatchNoteRequest request) {
        NoteType noteType = EnumParam.parse(NoteType.class, request.noteType(), "noteType");
        CatNote note = catNoteService.patch(user.userId(), noteId, noteType, request.body(), request.occurredOn());
        return CatNoteResponse.from(note);
    }

    /** D19 — xoá mềm ghi chú. */
    @Operation(operationId = "deleteCatNote", summary = "D19 — Xoá mềm ghi chú")
    @DeleteMapping("/cat-notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCatNote(@CurrentUser SecurityPrincipal user, @PathVariable UUID noteId) {
        catNoteService.softDelete(user.userId(), noteId);
    }

    // ============================================================== D20 — dấu hiệu lâm sàng

    /** D20 — khai dấu hiệu lâm sàng. */
    @Operation(operationId = "reportClinicalSigns", summary = "D20 — Khai dấu hiệu lâm sàng")
    @PostMapping("/cats/{catId}/clinical-signs")
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicalSignReportResponse reportClinicalSigns(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID catId,
            @Valid @RequestBody ReportClinicalSignsRequest request) {
        Set<String> signs = request.signs() == null ? Set.of() : new LinkedHashSet<>(request.signs());
        return ClinicalSignReportResponse.from(
                clinicalSignReportService.create(user.userId(), catId, signs, request.source()));
    }

    // ============================================================== F2 — giống mèo

    /** F2 — danh mục giống mèo đang hoạt động, nhãn theo locale. */
    @Operation(operationId = "listCatBreeds", summary = "F2 — Danh mục giống mèo")
    @GetMapping("/reference/cat-breeds")
    public CatBreedListResponse listCatBreeds(Locale locale) {
        boolean english = "en".equals(locale.getLanguage());
        List<CatBreedResponse> items = catProfileService.listActiveBreeds().stream()
                .map(breed -> CatBreedResponse.from(breed, english))
                .toList();
        return new CatBreedListResponse(items);
    }
}
