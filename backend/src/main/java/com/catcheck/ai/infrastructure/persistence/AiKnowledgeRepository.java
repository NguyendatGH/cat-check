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

    static final String ERASED_CONTENT = "[erased]";

    private final JdbcTemplate jdbc;

    public AiKnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasDocuments() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM ai_document WHERE active = TRUE", Integer.class);
        return count != null && count > 0;
    }

    private static final int MIN_MATCHED_TERMS = 2;

    private static final String STOPWORDS = String.join(" ",
            "là", "gì", "của", "tôi", "bạn", "mình", "em", "anh", "chị", "cho", "và", "có", "không",
            "thể", "được", "thì", "nên", "làm", "sao", "bao", "nhiêu", "lâu", "những", "các", "một",
            "này", "đó", "kia", "đã", "bị", "với", "trong", "khi", "để", "hay", "hoặc", "mà", "như",
            "ở", "về", "thế", "nào", "nay", "hôm", "ạ", "ơi", "nhé", "vậy", "rồi", "cần", "phải",
            "muốn", "sẽ", "đang", "ai", "đâu");

    public List<RetrievedChunk> search(String locale, String query, int limit) {
        List<String> contentTerms = jdbc.queryForList("""
                SELECT t FROM unnest(tsvector_to_array(to_tsvector('simple', ?))) t
                WHERE t <> ALL (string_to_array(?, ' '))
                """, String.class, query, STOPWORDS);
        if (contentTerms.isEmpty()) {
            return List.of();
        }
        String sql = """
                WITH q AS (SELECT plainto_tsquery('simple', ?) AS query)
                SELECT d.id, d.title, d.source_url, c.content,
                       row_number() OVER (ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC) AS rank
                FROM ai_chunk c
                JOIN ai_document d ON d.id = c.document_id
                JOIN care_tip t ON d.source_type = 'CARE_TIP' AND t.id = d.source_id
                     AND t.status = 'PUBLISHED' AND t.deleted_at IS NULL
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
        String relaxed = """
                WITH q AS (
                    SELECT to_tsquery('simple', replace(plainto_tsquery('simple', ?)::text, ' & ', ' | ')) AS query,
                           string_to_array(?, ' ') AS terms
                )
                SELECT d.id, d.title, d.source_url, c.content,
                       row_number() OVER (ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC) AS rank
                FROM ai_chunk c
                JOIN ai_document d ON d.id = c.document_id
                JOIN care_tip t ON d.source_type = 'CARE_TIP' AND t.id = d.source_id
                     AND t.status = 'PUBLISHED' AND t.deleted_at IS NULL
                CROSS JOIN q
                WHERE d.active = TRUE AND d.locale = ? AND c.search_vector @@ q.query
                  AND (SELECT count(*) FROM unnest(q.terms) term
                       WHERE term = ANY (tsvector_to_array(c.search_vector))) >= least(?, cardinality(q.terms))
                ORDER BY ts_rank(c.search_vector, q.query) DESC, d.updated_at DESC, c.ordinal
                LIMIT ?
                """;
        results = jdbc.query(relaxed, (rs, rowNum) -> new RetrievedChunk(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("source_url"),
                rs.getString("content"), rs.getInt("rank")),
                query, String.join(" ", contentTerms), locale, MIN_MATCHED_TERMS, limit);
        if (!results.isEmpty()) {
            return results;
        }
        String fallback = """
                SELECT d.id, d.title, d.source_url, c.content,
                       row_number() OVER (ORDER BY d.updated_at DESC, c.ordinal) AS rank
                FROM ai_chunk c
                JOIN ai_document d ON d.id = c.document_id
                JOIN care_tip t ON d.source_type = 'CARE_TIP' AND t.id = d.source_id
                     AND t.status = 'PUBLISHED' AND t.deleted_at IS NULL
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
        return jdbc.query("""
                SELECT role, content FROM ai_message WHERE conversation_id = ?
                ORDER BY created_at DESC, CASE role WHEN 'ASSISTANT' THEN 0 ELSE 1 END
                LIMIT ?
                """,
                (rs, rowNum) -> new String[]{rs.getString("role"), rs.getString("content")}, conversationId, limit)
                .reversed();
    }

    @Override
    public int eraseConversationContent(UUID userId) {
        return jdbc.update("""
                UPDATE ai_message m SET content = ?
                FROM ai_conversation c
                WHERE c.id = m.conversation_id AND c.user_id = ? AND m.content <> ?
                """, ERASED_CONTENT, userId, ERASED_CONTENT);
    }

    public void saveCitations(UUID messageId, List<RetrievedChunk> chunks) {
        jdbc.batchUpdate("INSERT INTO ai_citation(message_id, document_id, rank) VALUES (?, ?, ?)",
                chunks.stream().map(chunk -> new Object[]{messageId, chunk.documentId(), chunk.rank()}).toList());
    }

    @Transactional
    public int syncPublishedCareTips() {
        jdbc.update("UPDATE ai_document SET active = FALSE WHERE source_type = 'CARE_TIP'");
        List<SourceRow> sources = jdbc.query("""
                SELECT id, locale, slug, title, coalesce(summary, '') AS summary, coalesce(body_md, '') AS body,
                       coalesce(tags::text, '[]') AS tags
                FROM care_tip WHERE status = 'PUBLISHED' AND deleted_at IS NULL
                """, (rs, rowNum) -> new SourceRow(
                rs.getObject("id", UUID.class), rs.getString("locale"), rs.getString("slug"),
                rs.getString("title"), rs.getString("summary"), rs.getString("body"),
                parseJsonArray(rs.getString("tags"))));
        for (SourceRow source : sources) {
            UUID documentId = UUID.nameUUIDFromBytes(("care_tip:" + source.id() + ":" + source.locale()).getBytes(StandardCharsets.UTF_8));
            String content = tagLine(source.tags()) + source.title() + "\n\n" + source.summary() + "\n\n" + source.body();
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

    private record SourceRow(UUID id, String locale, String slug, String title, String summary, String body,
                              List<String> tags) {
    }

    private static String tagLine(List<String> tags) {
        return tags.isEmpty() ? "" : "[Chủ đề: " + String.join(", ", tags) + "]\n\n";
    }

    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String body = json.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : body.split(",")) {
            out.add(part.trim().replace("\"", ""));
        }
        return List.copyOf(out);
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
