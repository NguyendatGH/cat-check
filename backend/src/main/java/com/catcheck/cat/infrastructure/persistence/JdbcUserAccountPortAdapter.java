package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.OnboardingMilestone;
import com.catcheck.cat.application.spi.UserAccountPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JdbcUserAccountPortAdapter implements UserAccountPort {

    private final JdbcTemplate jdbc;

    JdbcUserAccountPortAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AccountStatus statusOf(UUID userId) {
        List<String> rows = jdbc.query(
                "SELECT status FROM app_user WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"),
                userId);
        return rows.isEmpty() ? null : AccountStatus.fromWire(rows.getFirst());
    }

    @Override
    public void advanceOnboardingStatus(UUID userId, OnboardingMilestone milestone) {
        jdbc.update(ADVANCE, milestone.wire(), userId, milestone.wire());
    }

    private static final String ADVANCE = """
            UPDATE app_user
               SET onboarding_status = ?
             WHERE id = ?
               AND array_position(
                       ARRAY['ACCOUNT_ONLY','CAT_CREATED','SURVEY_DONE_OR_SKIPPED','COMPLETED'],
                       onboarding_status)
                 < array_position(
                       ARRAY['ACCOUNT_ONLY','CAT_CREATED','SURVEY_DONE_OR_SKIPPED','COMPLETED'],
                       ?)
            """;
}
