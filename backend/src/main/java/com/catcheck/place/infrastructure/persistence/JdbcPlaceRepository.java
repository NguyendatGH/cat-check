package com.catcheck.place.infrastructure.persistence;

import com.catcheck.place.domain.Place;
import com.catcheck.place.domain.PlaceReview;
import com.catcheck.place.domain.port.PlaceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPlaceRepository implements PlaceRepository {
    private final JdbcTemplate jdbc;

    public JdbcPlaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Place> search(String query, String kind, String area, Double latitude, Double longitude, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.kind, p.address, p.area, p.latitude, p.longitude, p.phone,
                       p.specialties, p.badges,
                       coalesce(avg(r.rating), 0) AS rating, count(r.id) AS review_count
                  FROM place p LEFT JOIN place_review r ON r.place_id = p.id AND r.status = 'PUBLISHED'
                 WHERE p.status = 'PUBLISHED'
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (query != null && !query.isBlank()) { sql.append(" AND (lower(p.name) LIKE lower(?) OR lower(p.address) LIKE lower(?))"); args.add("%" + query.strip() + "%"); args.add("%" + query.strip() + "%"); }
        if (kind != null && !kind.isBlank()) { sql.append(" AND p.kind = ?"); args.add(kind.toUpperCase()); }
        if (area != null && !area.isBlank()) { sql.append(" AND p.area = ?"); args.add(area); }
        // Có vị trí: KHÔNG loại cơ sở xa, mà xếp gần → xa để luôn có "gần nhất" (trước đây hộp ±1° khiến
        // người dùng ngoài khu vực seed nhận danh sách rỗng). Khoảng cách phẳng hiệu chỉnh cos(vĩ độ).
        boolean nearest = latitude != null && longitude != null;
        sql.append(" GROUP BY p.id ORDER BY ");
        if (nearest) {
            sql.append("(p.latitude - ?) * (p.latitude - ?) + ((p.longitude - ?) * cos(radians(?))) * ((p.longitude - ?) * cos(radians(?))), ");
            args.add(latitude); args.add(latitude);
            args.add(longitude); args.add(latitude); args.add(longitude); args.add(latitude);
        }
        sql.append("p.name LIMIT ?"); args.add(Math.clamp(limit, 1, 100));
        return jdbc.query(sql.toString(), (rs, row) -> map(rs), args.toArray());
    }

    @Override
    public Optional<Place> find(UUID id) {
        return jdbc.query("""
                SELECT p.id, p.name, p.kind, p.address, p.area, p.latitude, p.longitude, p.phone,
                       p.specialties, p.badges, coalesce(avg(r.rating), 0) AS rating, count(r.id) AS review_count
                  FROM place p LEFT JOIN place_review r ON r.place_id = p.id AND r.status = 'PUBLISHED'
                 WHERE p.id = ? AND p.status = 'PUBLISHED' GROUP BY p.id
                """, (rs, row) -> map(rs), id).stream().findFirst();
    }

    @Override
    public List<PlaceReview> reviews(UUID placeId, int limit) {
        return jdbc.query("""
                SELECT id, rating, body, created_at
                  FROM place_review
                 WHERE place_id = ? AND status = 'PUBLISHED'
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """, (rs, row) -> new PlaceReview(
                rs.getObject("id", UUID.class), rs.getInt("rating"), rs.getString("body"),
                rs.getObject("created_at", OffsetDateTime.class)), placeId, Math.clamp(limit, 1, 100));
    }

    @Override
    public void createReview(UUID userId, UUID placeId, int rating, String body) {
        jdbc.update("""
                INSERT INTO place_review (place_id, user_id, rating, body)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (place_id, user_id) DO UPDATE SET rating = EXCLUDED.rating, body = EXCLUDED.body, updated_at = now()
                """, placeId, userId, rating, body);
    }

    @Override
    public void createBooking(UUID userId, UUID placeId, String serviceCode, LocalDate date, String timeSlot, String note) {
        try {
            jdbc.update("INSERT INTO place_booking (place_id, user_id, service_code, booking_date, time_slot, note) VALUES (?, ?, ?, ?, ?, ?)", placeId, userId, serviceCode, date, timeSlot, note);
        } catch (DuplicateKeyException ex) {
            throw new com.catcheck.shared.error.ConflictException(com.catcheck.place.api.PlaceErrorCode.BOOKING_SLOT_TAKEN);
        }
    }

    private Place map(ResultSet rs) throws SQLException {
        return new Place(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getString("address"),
                rs.getString("area"), rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getString("phone"),
                strings(rs.getArray("specialties")), strings(rs.getArray("badges")), rs.getDouble("rating"), rs.getLong("review_count"));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        try { return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList(); }
        finally { array.free(); }
    }
}
