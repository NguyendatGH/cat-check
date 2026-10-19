package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.CatProfileLimit;
import com.catcheck.cat.application.spi.CatProfileLimitPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcCatProfileLimitAdapter implements CatProfileLimitPort {

    private final JdbcTemplate jdbc;

    JdbcCatProfileLimitAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatProfileLimit> limitFor(UUID ownerId) {
        List<Integer> rows = jdbc.query(
                "SELECT max_cat_profiles FROM user_entitlement WHERE user_id = ?",
                (rs, rowNum) -> {
                    int value = rs.getInt("max_cat_profiles");
                    return rs.wasNull() ? null : value;
                },
                ownerId);
        if (rows.isEmpty() || rows.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(new CatProfileLimit(rows.getFirst(), null));
    }
}
