-- RAG knowledge base and chat history. Content is copied from published care_tip rows so
-- retrieval stays read-only with respect to the content module.
CREATE TABLE IF NOT EXISTS ai_document (
    id UUID PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    locale VARCHAR(8) NOT NULL,
    title TEXT NOT NULL,
    source_url TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_document_source UNIQUE (source_type, source_id, locale)
);

CREATE TABLE IF NOT EXISTS ai_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES ai_document(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    content TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    CONSTRAINT uq_ai_chunk_document_ordinal UNIQUE (document_id, ordinal),
    CONSTRAINT ck_ai_chunk_content CHECK (length(btrim(content)) BETWEEN 1 AND 8000)
);

CREATE INDEX IF NOT EXISTS idx_ai_chunk_search ON ai_chunk USING GIN (search_vector);

DROP TRIGGER IF EXISTS trg_ai_document_updated_at ON ai_document;
CREATE TRIGGER trg_ai_document_updated_at
    BEFORE UPDATE ON ai_document
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS ai_conversation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ai_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_message_content CHECK (length(btrim(content)) BETWEEN 1 AND 20000)
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user ON ai_conversation (user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_message_conversation ON ai_message (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS ai_citation (
    message_id UUID NOT NULL REFERENCES ai_message(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES ai_document(id),
    rank INTEGER NOT NULL,
    PRIMARY KEY (message_id, rank)
);
