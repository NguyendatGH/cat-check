-- Retain old documents for citation history, but exclude archived/deleted source content
-- from future retrieval. This avoids breaking ai_citation foreign keys while keeping the
-- knowledge base synchronized with published care tips.
ALTER TABLE ai_document
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_ai_document_active_locale
    ON ai_document (active, locale, updated_at DESC);
