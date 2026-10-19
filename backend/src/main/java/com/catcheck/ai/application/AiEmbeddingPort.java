package com.catcheck.ai.application;

import java.util.List;

/** Embedding chạy cục bộ (không gọi API ngoài). Không có model thì retrieval chỉ dùng nhánh lexical. */
public interface AiEmbeddingPort {

    AiEmbeddingPort NONE = new AiEmbeddingPort() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String modelId() {
            return "none";
        }

        @Override
        public float[] embedQuery(String text) {
            throw new IllegalStateException("embedding model not configured");
        }

        @Override
        public List<float[]> embedPassages(List<String> texts) {
            throw new IllegalStateException("embedding model not configured");
        }
    };

    boolean available();

    /** model@revision — vector của hai model khác nhau không được so với nhau. */
    String modelId();

    float[] embedQuery(String text);

    List<float[]> embedPassages(List<String> texts);
}
