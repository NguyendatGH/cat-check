package com.catcheck.community.infrastructure.persistence;

import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.CommunityReport;
import com.catcheck.community.domain.port.CommunityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCommunityRepository implements CommunityRepository {
    private final JdbcTemplate jdbc;

    public JdbcCommunityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CommunityPost> findPosts(UUID viewerId, String category, int offset, int limit) {
        String filter = category == null ? "" : " AND p.category = ?";
        Object[] args = category == null
                ? new Object[]{viewerId, viewerId, offset, limit}
                : new Object[]{viewerId, viewerId, category, offset, limit};
        return jdbc.query("""
                SELECT p.id, p.author_user_id, u.full_name, p.category, p.title, p.body, p.tags, p.image_url,
                       (SELECT count(*) FROM community_reaction r WHERE r.post_id = p.id AND r.reaction = 'LIKE') AS like_count,
                       (SELECT count(*) FROM community_comment c WHERE c.post_id = p.id AND c.status = 'PUBLISHED') AS comment_count,
                       EXISTS (SELECT 1 FROM community_reaction r WHERE r.post_id = p.id AND r.user_id = ?::uuid AND r.reaction = 'LIKE') AS liked,
                       EXISTS (SELECT 1 FROM community_reaction r WHERE r.post_id = p.id AND r.user_id = ?::uuid AND r.reaction = 'BOOKMARK') AS bookmarked,
                       p.created_at
                  FROM community_post p
                  JOIN app_user u ON u.id = p.author_user_id
                 WHERE p.status = 'PUBLISHED'""" + filter + " ORDER BY p.created_at DESC, p.id DESC OFFSET ? LIMIT ?",
                (rs, row) -> post(rs), args);
    }

    @Override
    public long countPosts(String category) {
        if (category == null) return jdbc.queryForObject("SELECT count(*) FROM community_post WHERE status = 'PUBLISHED'", Long.class);
        return jdbc.queryForObject("SELECT count(*) FROM community_post WHERE status = 'PUBLISHED' AND category = ?", Long.class, category);
    }

    @Override
    public java.util.Optional<CommunityPost> findPost(UUID viewerId, UUID postId) {
        return jdbc.query("""
                SELECT p.id, p.author_user_id, u.full_name, p.category, p.title, p.body, p.tags, p.image_url,
                       (SELECT count(*) FROM community_reaction r WHERE r.post_id = p.id AND r.reaction = 'LIKE') AS like_count,
                       (SELECT count(*) FROM community_comment c WHERE c.post_id = p.id AND c.status = 'PUBLISHED') AS comment_count,
                       EXISTS (SELECT 1 FROM community_reaction r WHERE r.post_id = p.id AND r.user_id = ?::uuid AND r.reaction = 'LIKE') AS liked,
                       EXISTS (SELECT 1 FROM community_reaction r WHERE r.post_id = p.id AND r.user_id = ?::uuid AND r.reaction = 'BOOKMARK') AS bookmarked,
                       p.created_at
                  FROM community_post p JOIN app_user u ON u.id = p.author_user_id
                 WHERE p.id = ? AND p.status = 'PUBLISHED'
                """, (rs, row) -> post(rs), viewerId, viewerId, postId).stream().findFirst();
    }

    @Override
    public List<CommunityComment> findComments(UUID postId, int limit) {
        return jdbc.query("""
                SELECT c.id, c.post_id, c.author_user_id, u.full_name, c.body, c.created_at
                  FROM community_comment c JOIN app_user u ON u.id = c.author_user_id
                 WHERE c.post_id = ? AND c.status = 'PUBLISHED'
                 ORDER BY c.created_at ASC, c.id ASC LIMIT ?
                """, (rs, row) -> new CommunityComment(
                rs.getObject("id", UUID.class), rs.getObject("post_id", UUID.class),
                rs.getObject("author_user_id", UUID.class), rs.getString("full_name"),
                rs.getString("body"), instant(rs, "created_at")), postId, limit);
    }

    @Override
    public CommunityPost insertPost(UUID authorId, String category, String title, String body, List<String> tags) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO community_post (author_user_id, category, title, body, tags)
                VALUES (?, ?, ?, ?, ?::varchar[]) RETURNING id
                """, UUID.class, authorId, category, title, body, toPgArray(tags));
        return findPost(authorId, id).orElseThrow();
    }

    @Override
    public CommunityComment insertComment(UUID authorId, UUID postId, String body) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO community_comment (post_id, author_user_id, body)
                SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM community_post WHERE id = ? AND status = 'PUBLISHED')
                RETURNING id
                """, UUID.class,
                postId, authorId, body, postId);
        return jdbc.queryForObject("""
                SELECT c.id, c.post_id, c.author_user_id, u.full_name, c.body, c.created_at
                  FROM community_comment c JOIN app_user u ON u.id = c.author_user_id
                 WHERE c.id = ?
                """, (rs, row) -> new CommunityComment(
                rs.getObject("id", UUID.class), rs.getObject("post_id", UUID.class),
                rs.getObject("author_user_id", UUID.class), rs.getString("full_name"),
                rs.getString("body"), instant(rs, "created_at")), id);
    }

    @Override
    public boolean toggleReaction(UUID userId, UUID postId, String reaction, boolean active) {
        if (active) {
            return jdbc.update("INSERT INTO community_reaction (post_id, user_id, reaction) VALUES (?, ?, ?) ON CONFLICT (post_id, user_id) DO UPDATE SET reaction = EXCLUDED.reaction", postId, userId, reaction) == 1;
        }
        return jdbc.update("DELETE FROM community_reaction WHERE post_id = ? AND user_id = ? AND reaction = ?", postId, userId, reaction) == 1;
    }

    @Override
    public void report(UUID userId, UUID postId, UUID commentId, String reason, String details) {
        jdbc.update("INSERT INTO community_report (post_id, comment_id, reporter_user_id, reason, details) VALUES (?, ?, ?, ?, ?)", postId, commentId, userId, reason, details);
    }

    @Override
    public List<CommunityReport> findReports(String status, int offset, int limit) {
        return jdbc.query(reportSql("WHERE r.status = ? ORDER BY r.created_at DESC, r.id DESC OFFSET ? LIMIT ?"),
                (rs, row) -> report(rs), status, offset, limit);
    }

    @Override
    public long countReports(String status) {
        return jdbc.queryForObject("SELECT count(*) FROM community_report WHERE status = ?", Long.class, status);
    }

    @Override
    public java.util.Optional<CommunityReport> findReport(UUID reportId) {
        return jdbc.query(reportSql("WHERE r.id = ?"), (rs, row) -> report(rs), reportId).stream().findFirst();
    }

    @Override
    public void moderateReport(UUID reportId, String action) {
        if ("HIDE_POST".equals(action) || "REMOVE_POST".equals(action)) {
            jdbc.update("UPDATE community_post SET status = ? WHERE id = (SELECT post_id FROM community_report WHERE id = ?)",
                    "HIDE_POST".equals(action) ? "HIDDEN" : "REMOVED", reportId);
        } else if ("HIDE_COMMENT".equals(action)) {
            jdbc.update("UPDATE community_comment SET status = 'HIDDEN' WHERE id = (SELECT comment_id FROM community_report WHERE id = ?)", reportId);
        }
        jdbc.update("UPDATE community_report SET status = ? WHERE id = ?",
                "DISMISS".equals(action) ? "DISMISSED" : "RESOLVED", reportId);
    }

    private String reportSql(String tail) {
        return """
                SELECT r.id, r.post_id, r.comment_id, reporter.full_name AS reporter_name,
                       r.reason, r.details, r.status,
                       coalesce(p.title, left(c.body, 120)) AS target_title, r.created_at
                  FROM community_report r
                  JOIN app_user reporter ON reporter.id = r.reporter_user_id
                  LEFT JOIN community_post p ON p.id = r.post_id
                  LEFT JOIN community_comment c ON c.id = r.comment_id
                """ + tail;
    }

    private CommunityReport report(ResultSet rs) throws SQLException {
        return new CommunityReport(rs.getObject("id", UUID.class), rs.getObject("post_id", UUID.class),
                rs.getObject("comment_id", UUID.class), rs.getString("reporter_name"), rs.getString("reason"),
                rs.getString("details"), rs.getString("status"), rs.getString("target_title"), instant(rs, "created_at"));
    }

    private CommunityPost post(ResultSet rs) throws SQLException {
        return new CommunityPost(rs.getObject("id", UUID.class), rs.getObject("author_user_id", UUID.class),
                rs.getString("full_name"), rs.getString("category"), rs.getString("title"), rs.getString("body"),
                strings(rs.getArray("tags")), rs.getString("image_url"), rs.getInt("like_count"), rs.getInt("comment_count"),
                rs.getBoolean("liked"), rs.getBoolean("bookmarked"), instant(rs, "created_at"));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        try { return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList(); }
        finally { array.free(); }
    }

    private static String toPgArray(List<String> values) {
        return "{" + values.stream().map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").reduce((a, b) -> a + "," + b).orElse("") + "}";
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
