package com.catcheck.ai.infrastructure.persistence;

import com.catcheck.ai.application.AiKnowledgePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Repository
public class AiKnowledgeRepository implements AiKnowledgePort {

    private final JdbcTemplate jdbc;

    public AiKnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasDocuments() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM ai_document WHERE active = TRUE", Integer.class);
        return count != null && count > 0;
    }

    public List<RetrievedChunk> search(String locale, String query, int limit) {
        String sql = """
                WITH q AS (SELECT plainto_tsquery('simple', ?) AS query)
                SELECT d.id, d.title, d.source_url, c.content,
                       row_number() OVER (ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC) AS rank
                FROM ai_chunk c
                JOIN ai_document d ON d.id = c.document_id
                CROSS JOIN q
                WHERE d.active = TRUE AND d.locale = ? AND c.search_vector @@ q.query
                ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC, c.ordinal
                LIMIT ?
                """;
        List<RetrievedChunk> results = jdbc.query(sql, (rs, rowNum) -> new RetrievedChunk(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("source_url"),
                rs.getString("content"), rs.getInt("rank")), query, locale, limit);
        if (!results.isEmpty()) {
            return results;
        }
        // Natural-language questions often contain connective/context words absent from a
        // short care tip. Preserve the precise AND search above first; only relax to OR when
        // it found nothing, ranking chunks that match more query terms ahead of weaker hits.
        String relaxed = """
                WITH q AS (
                    SELECT to_tsquery('simple', replace(plainto_tsquery('simple', ?)::text, ' & ', ' | ')) AS query
                )
                SELECT d.id, d.title, d.source_url, c.content,
                       row_number() OVER (ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC) AS rank
                FROM ai_chunk c
                JOIN ai_document d ON d.id = c.document_id
                CROSS JOIN q
                WHERE d.active = TRUE AND d.locale = ? AND c.search_vector @@ q.query
                ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC, c.ordinal
                LIMIT ?
                """;
        results = jdbc.query(relaxed, (rs, rowNum) -> new RetrievedChunk(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("source_url"),
                rs.getString("content"), rs.getInt("rank")), query, locale, limit);
        if (!results.isEmpty()) {
            return results;
        }
        String fallback = """
                SELECT d.id, d.title, d.source_url, c.content,
                       row_number() OVER (ORDER BY d.updated_at DESC, c.ordinal) AS rank
                FROM ai_chunk c
                JOIN ai_document d ON d.id = c.document_id
                WHERE d.active = TRUE AND d.locale = ? AND (d.title ILIKE ? OR c.content ILIKE ?)
                ORDER BY d.updated_at DESC, c.ordinal
                LIMIT ?
                """;
        String needle = "%" + query.strip() + "%";
        return jdbc.query(fallback, (rs, rowNum) -> new RetrievedChunk(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("source_url"),
                rs.getString("content"), rs.getInt("rank")), locale, needle, needle, limit);
    }

    public Conversation createConversation(UUID userId, UUID requestedId) {
        if (requestedId == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ai_conversation(id, user_id) VALUES (?, ?)", id, userId);
            return new Conversation(id, userId);
        }
        Conversation conversation = jdbc.query("SELECT id, user_id FROM ai_conversation WHERE id = ?",
                rs -> rs.next() ? new Conversation(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class)) : null,
                requestedId);
        if (conversation == null || !userId.equals(conversation.userId())) {
            return null;
        }
        return conversation;
    }

    public UUID saveMessage(UUID conversationId, String role, String content, String provider) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_message(id, conversation_id, role, content, provider) VALUES (?, ?, ?, ?, ?)",
                id, conversationId, role, content, provider);
        jdbc.update("UPDATE ai_conversation SET last_message_at = now() WHERE id = ?", conversationId);
        return id;
    }

    public List<String[]> history(UUID conversationId, int limit) {
        return jdbc.query("SELECT role, content FROM ai_message WHERE conversation_id = ? ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new String[]{rs.getString("role"), rs.getString("content")}, conversationId, limit)
                .reversed();
    }

    public void saveCitations(UUID messageId, List<RetrievedChunk> chunks) {
        jdbc.batchUpdate("INSERT INTO ai_citation(message_id, document_id, rank) VALUES (?, ?, ?)",
                chunks.stream().map(chunk -> new Object[]{messageId, chunk.documentId(), chunk.rank()}).toList());
    }

    @Transactional
    public int syncPublishedCareTips() {
        jdbc.update("UPDATE ai_document SET active = FALSE WHERE source_type = 'CARE_TIP'");
        List<SourceRow> sources = jdbc.query("""
                SELECT id, locale, slug, title, coalesce(summary, '') AS summary, coalesce(body_md, '') AS body
                FROM care_tip WHERE status = 'PUBLISHED' AND deleted_at IS NULL
                """, (rs, rowNum) -> new SourceRow(
                rs.getObject("id", UUID.class), rs.getString("locale"), rs.getString("slug"),
                rs.getString("title"), rs.getString("summary"), rs.getString("body")));
        for (SourceRow source : sources) {
            UUID documentId = UUID.nameUUIDFromBytes(("care_tip:" + source.id() + ":" + source.locale()).getBytes(StandardCharsets.UTF_8));
            String content = source.title() + "\n\n" + source.summary() + "\n\n" + source.body();
            jdbc.update("""
                    INSERT INTO ai_document(id, source_type, source_id, locale, title, source_url, content_hash)
                    VALUES (?, 'CARE_TIP', ?, ?, ?, ?, ?)
                    ON CONFLICT (source_type, source_id, locale) DO UPDATE SET
                      title = EXCLUDED.title, source_url = EXCLUDED.source_url,
                      content_hash = EXCLUDED.content_hash, active = TRUE, updated_at = now()
                    """, documentId, source.id(), source.locale(), source.title(), "/care-tips/" + source.slug(), sha256(content));
            jdbc.update("DELETE FROM ai_chunk WHERE document_id = ?", documentId);
            List<String> chunks = split(content, 1600, 160);
            for (int i = 0; i < chunks.size(); i++) {
                jdbc.update("INSERT INTO ai_chunk(id, document_id, ordinal, content) VALUES (?, ?, ?, ?)",
                        UUID.nameUUIDFromBytes((documentId + ":" + i).getBytes(StandardCharsets.UTF_8)), documentId, i, chunks.get(i));
            }
        }
        return sources.size();
    }

    private record SourceRow(UUID id, String locale, String slug, String title, String summary, String body) {
    }

    private static List<String> split(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) result.add(chunk);
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được hash knowledge document", ex);
        }
    }
}
