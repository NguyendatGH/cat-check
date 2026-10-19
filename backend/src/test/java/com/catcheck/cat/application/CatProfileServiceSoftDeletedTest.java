package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.AppSettingPort;
import com.catcheck.cat.application.spi.CatProfileLimitPort;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.port.CatBreedRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

class CatProfileServiceSoftDeletedTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID CAT_ID = UUID.randomUUID();

    private CatProfileService service;

    @BeforeEach
    void setUp() {
        Cat deleted = Cat.create(CAT_ID, OWNER_ID, "Mochi", null, 12, "CC-VN-ABC123",
                LocalDate.now(CLOCK), NOW);
        deleted.softDelete(NOW);
        CatRepository cats = mock(CatRepository.class);
        when(cats.findByIdAndOwnerId(CAT_ID, OWNER_ID)).thenReturn(Optional.of(deleted));
        UserAccountPort accounts = mock(UserAccountPort.class);
        when(accounts.statusOf(OWNER_ID)).thenReturn(AccountStatus.ACTIVE);
        service = new CatProfileService(cats, mock(CatBreedRepository.class), accounts,
                mock(CatProfileLimitPort.class), mock(AppSettingPort.class),
                mock(ApplicationEventPublisher.class), new UuidV7(CLOCK), CLOCK);
    }

    @Test
    void readingOrChangingASoftDeletedCatIsNotFound() {
        assertCatNotFound(() -> service.get(OWNER_ID, CAT_ID));
        assertCatNotFound(() -> service.patch(OWNER_ID, CAT_ID, new CatProfileService.PatchCatCommand(
                "Mun", true, null, null, null, null, null, null, null, null, null)));
        assertCatNotFound(() -> service.archive(OWNER_ID, CAT_ID));
        assertCatNotFound(() -> service.unarchive(OWNER_ID, CAT_ID));
    }

    @Test
    void deletingTwiceStillReportsAlreadyDeleted() {
        ConflictException ex = assertThrows(ConflictException.class, () -> service.softDelete(OWNER_ID, CAT_ID));

        assertEquals(CatErrorCode.CAT_ALREADY_DELETED, ex.errorCode());
    }

    private static void assertCatNotFound(Executable call) {
        CatCheckException ex = assertThrows(NotFoundException.class, call);
        assertEquals(CatErrorCode.CAT_NOT_FOUND, ex.errorCode());
    }
}
