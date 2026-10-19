-- Nhánh vector của Hybrid RAG (docs/AI_HYBRID_RAG_PLAN.md mục 3.4): pgvector + embedding cục bộ
-- multilingual-e5-small (384 chiều). Cần pgvector đã cài trên server Postgres.
-- Chưa tạo index ANN: corpus nhỏ dùng exact search; chỉ thêm HNSW khi EXPLAIN ANALYZE cho thấy cần.
-- embedding_model ghi model@revision đã sinh vector: đổi model thì embed lại, không trộn hai không gian vector.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE ai_chunk
    ADD COLUMN embedding vector(384),
    ADD COLUMN embedding_model VARCHAR(80);
