package com.catcheck.ai.infrastructure.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.catcheck.ai.application.AiEmbeddingPort;
import com.catcheck.ai.infrastructure.config.AiProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * multilingual-e5-small (ONNX int8, 384 chiều) chạy trong JVM — không gọi API ngoài cho mỗi câu hỏi.
 * e5 yêu cầu tiền tố "query: " / "passage: "; vector = mean pooling theo attention mask rồi chuẩn hoá L2,
 * nên cosine similarity = tích vô hướng. Thiếu thư mục model thì {@link #available()} = false.
 */
@Component
public class OnnxE5Embedder implements AiEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(OnnxE5Embedder.class);

    static final int DIMENSIONS = 384;
    static final String MODEL_ID = "multilingual-e5-small@761b726";
    private static final int MAX_TOKENS = 512;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public OnnxE5Embedder(AiProperties properties) {
        Path dir = properties.embeddingModelDir() == null || properties.embeddingModelDir().isBlank()
                ? null : Path.of(properties.embeddingModelDir());
        if (dir == null || !Files.isRegularFile(dir.resolve("model_quantized.onnx"))
                || !Files.isRegularFile(dir.resolve("tokenizer.json"))) {
            log.info("Local embedding model not configured (catcheck.ai.embedding-model-dir); vector retrieval disabled");
            this.environment = null;
            this.session = null;
            this.tokenizer = null;
            return;
        }
        // Tokenizer đọc file cục bộ; offline mode chặn DJL tự tải bất cứ thứ gì từ mạng.
        System.setProperty("ai.djl.offline", "true");
        HuggingFaceTokenizer loadedTokenizer = null;
        OrtEnvironment loadedEnvironment = null;
        OrtSession loadedSession = null;
        try {
            loadedTokenizer = HuggingFaceTokenizer.newInstance(dir.resolve("tokenizer.json"), Map.of(
                    "maxLength", String.valueOf(MAX_TOKENS), "truncation", "true", "padding", "true"));
            loadedEnvironment = OrtEnvironment.getEnvironment();
            loadedSession = loadedEnvironment.createSession(dir.resolve("model_quantized.onnx").toString(),
                    new OrtSession.SessionOptions());
            log.info("Local embedding model loaded: {}", MODEL_ID);
        } catch (IOException | OrtException | RuntimeException | LinkageError ex) {
            // Tính năng phụ không được làm sập cả app: hạ về retrieval lexical, báo lỗi rõ để vận hành sửa.
            log.error("Cannot load local embedding model from {} ({}); vector retrieval disabled",
                    dir, ex.getClass().getSimpleName());
            if (loadedTokenizer != null) loadedTokenizer.close();
            loadedTokenizer = null;
            loadedSession = null;
        }
        this.tokenizer = loadedTokenizer;
        this.environment = loadedEnvironment;
        this.session = loadedSession;
    }

    @Override
    public boolean available() {
        return session != null;
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(List.of("query: " + text)).getFirst();
    }

    @Override
    public List<float[]> embedPassages(List<String> texts) {
        return embed(texts.stream().map(text -> "passage: " + text).toList());
    }

    private List<float[]> embed(List<String> texts) {
        if (!available()) {
            throw new IllegalStateException("embedding model not configured");
        }
        Encoding[] encodings = tokenizer.batchEncode(texts);
        int batch = encodings.length;
        int length = encodings[0].getIds().length;
        long[][] ids = new long[batch][];
        long[][] mask = new long[batch][];
        long[][] types = new long[batch][];
        for (int i = 0; i < batch; i++) {
            ids[i] = encodings[i].getIds();
            mask[i] = encodings[i].getAttentionMask();
            types[i] = encodings[i].getTypeIds();
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(environment, ids));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, mask));
            if (session.getInputNames().contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(environment, types));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                List<float[]> vectors = new ArrayList<>(batch);
                for (int i = 0; i < batch; i++) {
                    vectors.add(meanPoolNormalized(hidden[i], mask[i], length));
                }
                return vectors;
            }
        } catch (OrtException ex) {
            throw new IllegalStateException("Embedding inference failed", ex);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    static float[] meanPoolNormalized(float[][] tokens, long[] mask, int length) {
        float[] pooled = new float[tokens[0].length];
        int count = 0;
        for (int t = 0; t < length; t++) {
            if (mask[t] == 0) continue;
            count++;
            for (int d = 0; d < pooled.length; d++) {
                pooled[d] += tokens[t][d];
            }
        }
        double norm = 0;
        for (int d = 0; d < pooled.length; d++) {
            pooled[d] /= Math.max(count, 1);
            norm += pooled[d] * pooled[d];
        }
        float scale = norm == 0 ? 1f : (float) (1 / Math.sqrt(norm));
        for (int d = 0; d < pooled.length; d++) {
            pooled[d] *= scale;
        }
        return pooled;
    }

    @PreDestroy
    void close() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }
}
