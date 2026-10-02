package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.CatResponse;
import com.catcheck.cat.application.CatAvatarService;
import com.catcheck.cat.application.CatProfileService;
import com.catcheck.cat.domain.Cat;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

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
    private final Clock clock;

    CatResponseAssembler(CatProfileService catProfileService, CatAvatarService catAvatarService, Clock clock) {
        this.catProfileService = catProfileService;
        this.catAvatarService = catAvatarService;
        this.clock = clock;
    }

    CatResponse toResponse(Cat cat, Locale locale) {
        boolean english = "en".equals(locale.getLanguage());
        String breedName = catProfileService.findBreed(cat.getBreedCode())
                .map(breed -> english ? breed.getNameEn() : breed.getNameVi())
                .orElse(null);
        String avatarUrl = catAvatarService.currentAvatarUrl(cat).map(URI::toString).orElse(null);
        return CatResponse.from(cat, breedName, avatarUrl, LocalDate.now(clock));
    }
}
