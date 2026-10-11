package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.AppSettingPort;
import com.catcheck.cat.application.spi.CatProfileLimitPort;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.port.CatBreedRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatProfileServiceAgeConflictTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID CAT_ID = UUID.randomUUID();

    @Test
    void createMapsBothAgeSourcesToCatAgeConflict() {
        CatRepository cats = writableRepository();
        when(cats.countActiveByOwnerId(OWNER_ID)).thenReturn(0L);
        when(cats.findPrimaryByOwnerId(OWNER_ID)).thenReturn(Optional.empty());
        when(cats.existsByPublicCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        CatProfileService service = service(cats);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.create(OWNER_ID,
                new CatProfileService.CreateCatCommand("Mochi", LocalDate.of(2024, 1, 1), 12,
                        null, null, null, null, null, null, false, null)));

        assertEquals(CatErrorCode.CAT_AGE_CONFLICT, ex.errorCode());
    }

    @Test
    void patchMapsBothAgeSourcesToTheSameCatAgeConflict() {
        CatRepository cats = writableRepository();
        Cat cat = Cat.create(CAT_ID, OWNER_ID, "Mochi", null, 12, "CC-VN-ABC123",
                LocalDate.now(CLOCK), NOW);
        when(cats.findByIdAndOwnerId(CAT_ID, OWNER_ID)).thenReturn(Optional.of(cat));
        CatProfileService service = service(cats);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.patch(OWNER_ID, CAT_ID,
                new CatProfileService.PatchCatCommand(null, false, LocalDate.of(2024, 1, 1), 12,
                        null, null, null, null, null, null, null)));

        assertEquals(CatErrorCode.CAT_AGE_CONFLICT, ex.errorCode());
    }

    private static CatProfileService service(CatRepository cats) {
        UserAccountPort accounts = mock(UserAccountPort.class);
        when(accounts.statusOf(OWNER_ID)).thenReturn(AccountStatus.ACTIVE);
        return new CatProfileService(cats, mock(CatBreedRepository.class), accounts,
                mock(CatProfileLimitPort.class), mock(AppSettingPort.class),
                mock(ApplicationEventPublisher.class), new UuidV7(CLOCK), CLOCK);
    }

    private static CatRepository writableRepository() {
        return mock(CatRepository.class);
    }
}
