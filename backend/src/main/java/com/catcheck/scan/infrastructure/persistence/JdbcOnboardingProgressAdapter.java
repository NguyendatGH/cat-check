package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.application.spi.OnboardingProgressPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcOnboardingProgressAdapter implements OnboardingProgressPort {

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
