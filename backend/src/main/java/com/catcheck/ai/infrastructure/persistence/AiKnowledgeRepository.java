package com.catcheck.ai.infrastructure.persistence;

import com.catcheck.ai.application.AiChatService;
import com.catcheck.ai.application.AiKnowledgePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiKnowledgeRepository implements AiKnowledgePort {

    static final String ERASED_CONTENT = "[erased]";

    // Đổi khi đổi cách chia chunk: hash tài liệu đổi theo nên lần sync sau chia lại toàn bộ.
    static final String CHUNKER_VERSION = "paragraph-v1";
    static final int CHUNK_MAX_CHARS = 1500;
    static final int CHUNK_OVERLAP_CHARS = 150;
    // Âm tiết tiếng Việt ngắn hơn chỉ khác nhau một chữ là thành từ khác (hang/han/hanh): chỉ đoán lỗi gõ
    // cho từ dài, vd "khayy" → "khay".
    private static final int MIN_FUZZY_TERM_LENGTH = 5;

    private static final String PUBLISHED_CHUNKS = """
            FROM ai_chunk c
            JOIN ai_document d ON d.id = c.document_id
            JOIN care_tip t ON d.source_type = 'CARE_TIP' AND t.id = d.source_id
                 AND t.status = 'PUBLISHED' AND t.deleted_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    public AiKnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CandidateSet searchCandidates(String locale, List<String> terms, float[] queryEmbedding,
                                         String embeddingModel, int perBranch) {
        if (terms.isEmpty() && queryEmbedding == null) {
            return new CandidateSet(List.of(), List.of());
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("WITH ");
        boolean lexical = !terms.isEmpty();
        if (lexical) {
            // Từ đã là [a-z0-9]+ (AiText) nên ghép vào tsquery an toàn. OR lọc ứng viên (dùng được index),
            // rồi đòi khớp ≥ một nửa số từ (tối thiểu min(2, số từ)) — đúng hoặc gần đúng (trigram, chịu lỗi
            // gõ) — để câu lạc đề hay câu dài chỉ trùng vài từ phổ biến không kéo tài liệu vào prompt.
            sql.append("""
                    q AS (SELECT to_tsquery('simple', ?) AS tsq, ?::text[] AS terms),
                    lex AS (
                      SELECT c.id, c.content_hash, d.id AS doc_id, d.title, d.source_url, c.content, c.ordinal,
                             m.matched + ts_rank_cd(c.search_folded, q.tsq) AS score
                    """).append(PUBLISHED_CHUNKS).append("""
                      CROSS JOIN q
                      CROSS JOIN LATERAL (
                        SELECT count(*) AS matched FROM unnest(q.terms) term
                        WHERE term = ANY (tsvector_to_array(c.search_folded))
                           OR (length(term) >= ? AND c.folded %> term)
                      ) m
                      WHERE d.active = TRUE AND d.locale = ?
                        AND (c.search_folded @@ q.tsq
                             OR EXISTS (SELECT 1 FROM unnest(q.terms) term WHERE length(term) >= ? AND c.folded %> term))
                        AND m.matched >= greatest(least(2, cardinality(q.terms)), ceil(cardinality(q.terms) / 2.0))
                      ORDER BY score DESC, c.ordinal
                      LIMIT ?
                    )""");
            args.add(String.join(" | ", terms));
            args.add(toPgArray(terms));
            args.add(MIN_FUZZY_TERM_LENGTH);
            args.add(locale);
            args.add(MIN_FUZZY_TERM_LENGTH);
            args.add(perBranch);
        }
        boolean vector = queryEmbedding != null;
        if (vector) {
            sql.append(lexical ? ",\n" : "").append("""
                    vec AS (
                      SELECT c.id, c.content_hash, d.id AS doc_id, d.title, d.source_url, c.content, c.ordinal,
                             1 - (c.embedding <=> ?::vector) AS score
                    """).append(PUBLISHED_CHUNKS).append("""
                      WHERE d.active = TRUE AND d.locale = ? AND c.embedding_model = ?
                      ORDER BY c.embedding <=> ?::vector
                      LIMIT ?
                    )""");
            String literal = toVectorLiteral(queryEmbedding);
            args.add(literal);
            args.add(locale);
            args.add(embeddingModel);
            args.add(literal);
            args.add(perBranch);
        }
        sql.append("\n");
        if (lexical) {
            sql.append("SELECT 'L' AS branch, * FROM lex");
        }
        if (vector) {
            sql.append(lexical ? " UNION ALL " : "").append("SELECT 'V' AS branch, * FROM vec");
        }
        sql.append(" ORDER BY branch, score DESC, ordinal");

        List<RetrievedChunk> lexicalHits = new ArrayList<>();
        List<RetrievedChunk> vectorHits = new ArrayList<>();
        jdbc.query(sql.toString(), rs -> {
            boolean isLexical = "L".equals(rs.getString("branch"));
            List<RetrievedChunk> target = isLexical ? lexicalHits : vectorHits;
            target.add(chunk(rs, target.size() + 1));
        }, args.toArray());
        return new CandidateSet(List.copyOf(lexicalHits), List.copyOf(vectorHits));
    }

    private static RetrievedChunk chunk(ResultSet rs, int rank) throws SQLException {
        return new RetrievedChunk(rs.getObject("id", UUID.class), rs.getString("content_hash"),
                rs.getObject("doc_id", UUID.class), rs.getString("title"), rs.getString("source_url"),
                rs.getString("content"), rank, rs.getDouble("score"));
    }

    @Override
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

    @Override
    public Optional<UUID> saveUserMessage(UUID conversationId, String content, UUID requestKey) {
        UUID id = UUID.randomUUID();
        int inserted = insertUserMessage(id, conversationId, content, requestKey);
        if (inserted == 0 && releaseOrphanedRequestKey(requestKey) > 0) {
            inserted = insertUserMessage(id, conversationId, content, requestKey);
        }
        if (inserted == 0) {
            return Optional.empty();
        }
        touchConversation(conversationId);
        return Optional.of(id);
    }

    private int insertUserMessage(UUID id, UUID conversationId, String content, UUID requestKey) {
        return jdbc.update("""
                INSERT INTO ai_message(id, conversation_id, role, content, provider, request_key)
                VALUES (?, ?, 'USER', ?, 'user', ?)
                ON CONFLICT (request_key, role) WHERE request_key IS NOT NULL DO NOTHING
                """, id, conversationId, content, requestKey);
    }

    /**
     * Process chết giữa lượt hỏi để lại câu hỏi giữ key mà không có câu trả lời. Quá 2 phút (dài hơn
     * connect + read timeout của provider) thì chắc chắn không còn request nào đang chạy với key đó.
     */
    private int releaseOrphanedRequestKey(UUID requestKey) {
        return jdbc.update("""
                UPDATE ai_message SET request_key = NULL
                WHERE request_key = ? AND role = 'USER' AND created_at < now() - INTERVAL '2 minutes'
                  AND NOT EXISTS (SELECT 1 FROM ai_message a WHERE a.request_key = ? AND a.role = 'ASSISTANT')
                """, requestKey, requestKey);
    }

    @Override
    public void releaseRequestKey(UUID requestKey) {
        jdbc.update("UPDATE ai_message SET request_key = NULL WHERE request_key = ? AND role = 'USER'", requestKey);
    }

    @Override
    public UUID saveAssistantMessage(UUID conversationId, String content, String provider, UUID requestKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_message(id, conversation_id, role, content, provider, request_key)
                VALUES (?, ?, 'ASSISTANT', ?, ?, ?)
                """, id, conversationId, content, provider, requestKey);
        touchConversation(conversationId);
        return id;
    }

    private void touchConversation(UUID conversationId) {
        jdbc.update("UPDATE ai_conversation SET last_message_at = now() WHERE id = ?", conversationId);
    }

    @Override
    public Optional<AiChatService.ChatResult> findReply(UUID userId, UUID requestKey) {
        return jdbc.query("""
                SELECT m.id, m.conversation_id, m.content, m.provider, m.created_at
                FROM ai_message m JOIN ai_conversation c ON c.id = m.conversation_id
                WHERE m.request_key = ? AND m.role = 'ASSISTANT' AND c.user_id = ?
                """, (rs, row) -> new AiChatService.ChatResult(
                        rs.getObject("conversation_id", UUID.class), rs.getObject("id", UUID.class),
                        rs.getString("content"), rs.getString("provider"), citationsOf(rs.getObject("id", UUID.class)),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()),
                requestKey, userId).stream().findFirst();
    }

    private List<AiChatService.Citation> citationsOf(UUID messageId) {
        return jdbc.query("""
                SELECT d.title, d.source_url, ci.rank FROM ai_citation ci JOIN ai_document d ON d.id = ci.document_id
                WHERE ci.message_id = ? ORDER BY ci.rank
                """, (rs, row) -> new AiChatService.Citation(rs.getString("title"), rs.getString("source_url"),
                rs.getInt("rank")), messageId);
    }

    @Override
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

    @Override
    public void saveCitations(UUID messageId, List<RetrievedChunk> chunks) {
        jdbc.batchUpdate("INSERT INTO ai_citation(message_id, document_id, rank, chunk_id, chunk_hash) VALUES (?, ?, ?, ?, ?)",
                chunks.stream().map(chunk -> new Object[]{messageId, chunk.documentId(), chunk.rank(),
                        chunk.chunkId(), chunk.chunkHash()}).toList());
    }

    @Override
    @Transactional
    public SyncResult syncPublishedCareTips() {
        List<SourceRow> sources = jdbc.query("""
                SELECT id, locale, slug, title, coalesce(summary, '') AS summary, coalesce(body_md, '') AS body,
                       coalesce(tags::text, '[]') AS tags
                FROM care_tip WHERE status = 'PUBLISHED' AND deleted_at IS NULL
                """, (rs, rowNum) -> new SourceRow(
                rs.getObject("id", UUID.class), rs.getString("locale"), rs.getString("slug"),
                rs.getString("title"), rs.getString("summary"), rs.getString("body"),
                parseJsonArray(rs.getString("tags"))));
        Map<UUID, String> indexed = new HashMap<>();
        jdbc.query("SELECT id, content_hash FROM ai_document WHERE source_type = 'CARE_TIP' AND active = TRUE",
                rs -> { indexed.put(rs.getObject("id", UUID.class), rs.getString("content_hash")); });

        List<UUID> live = new ArrayList<>();
        int changed = 0;
        for (SourceRow source : sources) {
            UUID documentId = UUID.nameUUIDFromBytes(("care_tip:" + source.id() + ":" + source.locale()).getBytes(StandardCharsets.UTF_8));
            live.add(documentId);
            String content = tagLine(source.tags()) + source.title() + "\n\n" + source.summary() + "\n\n" + source.body();
            String hash = sha256(CHUNKER_VERSION + "\n" + content);
            if (hash.equals(indexed.get(documentId))) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO ai_document(id, source_type, source_id, locale, title, source_url, content_hash)
                    VALUES (?, 'CARE_TIP', ?, ?, ?, ?, ?)
                    ON CONFLICT (source_type, source_id, locale) DO UPDATE SET
                      title = EXCLUDED.title, source_url = EXCLUDED.source_url,
                      content_hash = EXCLUDED.content_hash, active = TRUE, updated_at = now()
                    """, documentId, source.id(), source.locale(), source.title(), "/care-tips/" + source.slug(), hash);
            syncChunks(documentId, chunk(source.title(), content));
            changed++;
        }
        int removed = jdbc.update("""
                UPDATE ai_document SET active = FALSE
                WHERE source_type = 'CARE_TIP' AND active = TRUE AND id <> ALL (?::uuid[])
                """, toPgArray(live.stream().map(UUID::toString).toList()));
        return new SyncResult(sources.size(), changed, removed);
    }

    /** Chỉ ghi chunk đổi hash; chunk ghi lại mất embedding cũ để được embed lại. */
    private void syncChunks(UUID documentId, List<String> chunks) {
        Map<Integer, String> existing = new HashMap<>();
        jdbc.query("SELECT ordinal, content_hash FROM ai_chunk WHERE document_id = ?",
                rs -> { existing.put(rs.getInt("ordinal"), rs.getString("content_hash")); }, documentId);
        for (int i = 0; i < chunks.size(); i++) {
            String hash = sha256(chunks.get(i));
            if (hash.equals(existing.get(i))) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO ai_chunk(id, document_id, ordinal, content, content_hash) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (document_id, ordinal) DO UPDATE SET
                      content = EXCLUDED.content, content_hash = EXCLUDED.content_hash,
                      embedding = NULL, embedding_model = NULL
                    """, UUID.nameUUIDFromBytes((documentId + ":" + i).getBytes(StandardCharsets.UTF_8)),
                    documentId, i, chunks.get(i), hash);
        }
        jdbc.update("DELETE FROM ai_chunk WHERE document_id = ? AND ordinal >= ?", documentId, chunks.size());
    }

    @Override
    public List<PendingChunk> chunksNeedingEmbedding(String embeddingModel, int limit) {
        return jdbc.query("""
                SELECT c.id, c.content FROM ai_chunk c JOIN ai_document d ON d.id = c.document_id
                WHERE d.active = TRUE AND (c.embedding IS NULL OR c.embedding_model IS DISTINCT FROM ?)
                ORDER BY c.id
                LIMIT ?
                """, (rs, row) -> new PendingChunk(rs.getObject("id", UUID.class), rs.getString("content")),
                embeddingModel, limit);
    }

    @Override
    public void saveEmbeddings(String embeddingModel, Map<UUID, float[]> embeddings) {
        jdbc.batchUpdate("UPDATE ai_chunk SET embedding = ?::vector, embedding_model = ? WHERE id = ?",
                embeddings.entrySet().stream()
                        .map(entry -> new Object[]{toVectorLiteral(entry.getValue()), embeddingModel, entry.getKey()})
                        .toList());
    }

    private record SourceRow(UUID id, String locale, String slug, String title, String summary, String body,
                              List<String> tags) {
    }

    /**
     * Gom đoạn văn (ngăn bởi dòng trống) thành chunk ≤ {@link #CHUNK_MAX_CHARS} ký tự (~300–500 token),
     * nối thêm đuôi chunk trước để giữ mạch; chunk sau chunk đầu mang tiêu đề để vẫn tự đứng được khi truy hồi.
     */
    static List<String> chunk(String title, String content) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String text = paragraph.strip();
            if (text.isEmpty()) continue;
            if (text.length() <= CHUNK_MAX_CHARS) {
                pieces.add(text);
            } else {
                pieces.addAll(window(text, CHUNK_MAX_CHARS, CHUNK_OVERLAP_CHARS));
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (!current.isEmpty() && current.length() + 2 + piece.length() > CHUNK_MAX_CHARS) {
                chunks.add(current.toString());
                current = new StringBuilder(tail(current.toString()));
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(piece);
        }
        if (!current.isEmpty()) chunks.add(current.toString());
        for (int i = 1; i < chunks.size(); i++) {
            chunks.set(i, title + "\n\n" + chunks.get(i));
        }
        return chunks;
    }

    private static String tail(String text) {
        if (text.length() <= CHUNK_OVERLAP_CHARS) return "";
        String tail = text.substring(text.length() - CHUNK_OVERLAP_CHARS);
        int space = tail.indexOf(' ');
        return "…" + (space < 0 ? tail : tail.substring(space + 1));
    }

    private static List<String> window(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            String piece = text.substring(start, end).strip();
            if (!piece.isEmpty()) result.add(piece);
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
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

    private static String toPgArray(List<String> values) {
        return "{" + String.join(",", values) + "}";
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được hash knowledge document", ex);
        }
    }
}
