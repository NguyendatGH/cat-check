package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.domain.port.SubjectSnapshotPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiện thực {@link SubjectSnapshotPort} bằng JDBC thuần đọc {@code cat}/{@code app_user} — xem
 * javadoc cổng và {@code docs/handovers/A6.md} (judgment call, giống {@code scan}'s
 * {@code CatOwnershipPort}).
 */
@Repository
class JdbcSubjectSnapshotAdapter implements SubjectSnapshotPort {

    private final JdbcTemplate jdbc;

    JdbcSubjectSnapshotAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatProfile> findCatProfile(UUID catId) {
        String sql = """
                SELECT id, owner_id, name, breed_code, breed_other, sex, birth_date,
                       approx_age_months, weight_kg, avatar_storage_key
                  FROM cat
                 WHERE id = ?
                """;
        List<CatProfile> rows = jdbc.query(sql, (rs, n) -> {
            String breed = rs.getString("breed_other");
            if (breed == null || breed.isBlank()) {
                breed = rs.getString("breed_code");
            }
            return new CatProfile(
                    rs.getObject("id", UUID.class),
                    rs.getObject("owner_id", UUID.class),
                    rs.getString("name"),
                    breed,
                    rs.getString("sex"),
                    rs.getObject("birth_date", java.time.LocalDate.class),
                    (Integer) rs.getObject("approx_age_months"),
                    rs.getBigDecimal("weight_kg"),
                    rs.getString("avatar_storage_key"));
        }, catId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<OwnerProfile> findOwnerProfile(UUID userId) {
        String sql = "SELECT id, full_name, locale, timezone FROM app_user WHERE id = ?";
        List<OwnerProfile> rows = jdbc.query(sql, (rs, n) -> new OwnerProfile(
                rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("locale"), rs.getString("timezone")), userId);
        return rows.stream().findFirst();
    }
}
