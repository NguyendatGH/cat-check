package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.HolidayCalendarEntry;
import com.catcheck.privacy.domain.HolidaySource;
import com.catcheck.privacy.domain.port.HolidayCalendarPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * {@link HolidayCalendarPort} trên {@code JdbcTemplate} — trả tất cả ngày lễ; chỉ
 * {@code OFFICIAL} được dùng tính SLA, {@code COMPANY} không kéo dài SLA luật định (p4 B8).
 */
@Repository
public class JdbcHolidayCalendarAdapter implements HolidayCalendarPort {

    private final JdbcTemplate jdbc;

    public JdbcHolidayCalendarAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<HolidayCalendarEntry> allHolidays() {
        return new java.util.HashSet<>(jdbc.query("""
                SELECT holiday_date, country_code, name_vi, source, created_at
                  FROM holiday_calendar
                 ORDER BY holiday_date
                """, (rs, rowNum) -> new HolidayCalendarEntry(
                        RowReaders.localDate(rs, "holiday_date"),
                        RowReaders.requiredString(rs, "country_code"),
                        RowReaders.requiredString(rs, "name_vi"),
                        HolidaySource.valueOf(rs.getString("source")),
                        RowReaders.requiredInstant(rs, "created_at"))));
    }
}
