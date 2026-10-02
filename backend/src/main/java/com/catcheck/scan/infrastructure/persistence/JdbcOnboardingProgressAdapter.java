package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.application.spi.OnboardingProgressPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link OnboardingProgressPort} ghi thang cot {@code app_user.onboarding_status} bang SQL tho.
 *
 * <p>KHONG import class nao cua {@code com.catcheck.identity.*} nen khong sinh canh phu thuoc
 * Java moi — dung ky thuat {@code scan/package-info.java} da ghi chu cong khai (scan doc bang
 * cua cat bang JDBC read-only) va {@code cat.infrastructure.persistence.JdbcUserAccountPortAdapter}
 * da dung de doc {@code app_user.status}.</p>
 */
@Repository
class JdbcOnboardingProgressAdapter implements OnboardingProgressPort {

    /**
     * Chi tien, khong lui — so sanh vi tri trong mang thu tu ngay trong SQL nen thao tac la
     * nguyen tu. Mang viet thang trong cau lenh (hang so) thay vi bind {@code String[]}: driver
     * PostgreSQL khong tu suy duoc kieu SQL cho mang Java.
     */
    private static final String ADVANCE = """
            UPDATE app_user
               SET onboarding_status = 'COMPLETED'
             WHERE id = ?
               AND array_position(
                       ARRAY['ACCOUNT_ONLY','CAT_CREATED','SURVEY_DONE_OR_SKIPPED','COMPLETED'],
                       onboarding_status)
                 < array_position(
                       ARRAY['ACCOUNT_ONLY','CAT_CREATED','SURVEY_DONE_OR_SKIPPED','COMPLETED'],
                       'COMPLETED')
            """;

    private final JdbcTemplate jdbc;

    JdbcOnboardingProgressAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void markFirstScanCompleted(UUID userId) {
        jdbc.update(ADVANCE, userId);
    }
}
