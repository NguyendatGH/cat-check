-- Hybrid RAG cho trợ lý (docs/AI_HYBRID_RAG_PLAN.md mục 3–5):
--  * lexical tiếng Việt gõ không dấu / sai chính tả nhẹ (unaccent + pg_trgm), thay search_vector cũ;
--  * reindex gia tăng: hash từng chunk để chỉ cập nhật/embed lại phần đổi;
--  * citation giữ đúng phiên bản chunk sau reindex;
--  * idempotency cho POST /api/v1/ai/chat (một request key = một lượt hỏi).
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() chỉ STABLE; với từ điển cố định thì kết quả bất biến nên bọc lại IMMUTABLE để dùng
-- trong cột sinh và index. Câu hỏi được chuẩn hoá cùng hàm này ở phía truy vấn.
CREATE OR REPLACE FUNCTION ai_fold(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, $1)) $$;

ALTER TABLE ai_chunk DROP COLUMN search_vector;
ALTER TABLE ai_chunk
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN folded TEXT GENERATED ALWAYS AS (ai_fold(content)) STORED,
    ADD COLUMN search_folded TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', ai_fold(content))) STORED;

CREATE INDEX idx_ai_chunk_search_folded ON ai_chunk USING GIN (search_folded);
CREATE INDEX idx_ai_chunk_folded_trgm ON ai_chunk USING GIN (folded gin_trgm_ops);

-- Không FK tới ai_chunk: chunk có thể bị thay khi reindex, citation vẫn phải giữ được phiên bản đã trích.
ALTER TABLE ai_citation
    ADD COLUMN chunk_id UUID,
    ADD COLUMN chunk_hash VARCHAR(64);

ALTER TABLE ai_message ADD COLUMN request_key UUID;
CREATE UNIQUE INDEX uq_ai_message_request_key ON ai_message (request_key, role) WHERE request_key IS NOT NULL;
