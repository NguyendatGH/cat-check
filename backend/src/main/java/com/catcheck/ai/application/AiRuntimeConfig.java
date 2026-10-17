package com.catcheck.ai.application;

/** @param minVectorSimilarity cosine tối thiểu để một ứng viên chỉ-vector được vào context (đo lại bằng golden set) */
public record AiRuntimeConfig(int maxContextChunks, int maxHistoryMessages, double minVectorSimilarity) {

    // multilingual-e5-small dồn cosine vào 0.75–0.95: trên 5 bài seed, cặp đúng ≥ 0.90, cặp sai ≤ 0.86,
    // câu lạc đề ≤ 0.83. Đo lại bằng golden set khi có kho tri thức thật.
    public static final double DEFAULT_MIN_VECTOR_SIMILARITY = 0.88;

    public AiRuntimeConfig(int maxContextChunks, int maxHistoryMessages) {
        this(maxContextChunks, maxHistoryMessages, DEFAULT_MIN_VECTOR_SIMILARITY);
    }
}
