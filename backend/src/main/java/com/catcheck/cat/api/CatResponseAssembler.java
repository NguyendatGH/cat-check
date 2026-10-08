package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.CatResponse;
import com.catcheck.cat.application.CatAvatarService;
import com.catcheck.cat.application.CatProfileService;
import com.catcheck.cat.application.spi.ScanInsightPort;
import com.catcheck.cat.domain.Cat;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dựng {@link CatResponse} từ {@link Cat} — tách khỏi {@link CatController} có chủ đích: R4 cấm
 * {@code @RestController} có bất kỳ method nào (kể cả {@code private}) mang tham số/kiểu trả về
 * nằm trong {@code ..domain..} — ArchUnit quét TOÀN BỘ method của class, không chỉ method có
 * {@code @Operation}. Đặt logic này ở một class KHÔNG mang {@code @RestController} thì {@code Cat}
 * được phép xuất hiện trong chữ ký thoải mái (đã xác nhận bằng {@code mvn test} thật —
 * {@code WebLayerRuleTests#r4_restControllersDoNotExposeDomainTypes} đỏ khi method này còn nằm
 * trong {@code CatController}).
 */
@Component
class CatResponseAssembler {

    private final CatProfileService catProfileService;
    private final CatAvatarService catAvatarService;
    private final ScanInsightPort scanInsightPort;
    private final Clock clock;

    CatResponseAssembler(CatProfileService catProfileService, CatAvatarService catAvatarService,
                         ScanInsightPort scanInsightPort, Clock clock) {
        this.scanInsightPort = scanInsightPort;
        this.catProfileService = catProfileService;
        this.catAvatarService = catAvatarService;
        this.clock = clock;
    }

    CatResponse toResponse(Cat cat, Locale locale) {
        return toResponses(List.of(cat), locale, null).getFirst();
    }

    /**
     * Dựng danh sách; lần quét gần nhất và số cảnh báo lấy bằng đúng hai truy vấn cho cả danh sách.
     *
     * @param sort {@code "lastScanAt"} thì sắp xếp theo lần quét gần nhất (mới nhất trước, mèo chưa
     *             quét xuống cuối); giá trị khác giữ nguyên thứ tự gốc.
     */
    List<CatResponse> toResponses(List<Cat> cats, Locale locale, String sort) {
        List<UUID> ids = cats.stream().map(Cat::getId).toList();
        Map<UUID, ScanInsightPort.LastScan> lastScans = scanInsightPort.lastScansOf(ids);
        Map<UUID, Long> flags = scanInsightPort.unacknowledgedFlagCounts(ids);
        boolean english = "en".equals(locale.getLanguage());
        List<CatResponse> items = cats.stream().map(cat -> {
            String breedName = catProfileService.findBreed(cat.getBreedCode())
                    .map(breed -> english ? breed.getNameEn() : breed.getNameVi())
                    .orElse(null);
            String avatarUrl = catAvatarService.currentAvatarUrl(cat).map(URI::toString).orElse(null);
            ScanInsightPort.LastScan last = lastScans.get(cat.getId());
            return CatResponse.from(cat, breedName, avatarUrl, LocalDate.now(clock),
                    last == null ? null : last.capturedAt(),
                    last == null ? null : last.classification(),
                    flags.getOrDefault(cat.getId(), 0L));
        }).toList();
        if ("lastScanAt".equals(sort)) {
            return items.stream()
                    .sorted(Comparator.comparing(CatResponse::lastScanAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        return items;
    }
}
