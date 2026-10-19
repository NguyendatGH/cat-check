package com.catcheck.ai.infrastructure.embedding;

import com.catcheck.ai.infrastructure.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OnnxE5EmbedderTest {

    // Model ~118MB không nằm trong repo: tải bằng tools/fetch-embedding-model.sh; thiếu thì bỏ qua test.
    private static final Path MODEL_DIR = Path.of("data/models/multilingual-e5-small");

    @Test
    void missingModelDirectoryDisablesVectorRetrievalInsteadOfFailing() {
        assertThat(new OnnxE5Embedder(properties("/nonexistent")).available()).isFalse();
        assertThat(new OnnxE5Embedder(properties(null)).available()).isFalse();
    }

    @Test
    void meanPoolingIgnoresPaddingAndNormalizes() {
        float[] pooled = OnnxE5Embedder.meanPoolNormalized(
                new float[][]{{3, 0}, {3, 0}, {100, 100}}, new long[]{1, 1, 0}, 3);

        assertThat(pooled).containsExactly(1f, 0f);
    }

    @Test
    void sameMeaningIsCloserThanAnotherTopicEvenWithoutDiacritics() {
        assumeTrue(Files.isRegularFile(MODEL_DIR.resolve("model_quantized.onnx")), "embedding model not downloaded");
        OnnxE5Embedder embedder = new OnnxE5Embedder(properties(MODEL_DIR.toAbsolutePath().toString()));

        float[] query = embedder.embedQuery("meo uong it nuoc phai lam sao");
        List<float[]> passages = embedder.embedPassages(List.of(
                "Khuyến khích mèo uống nước: đặt nhiều bát nước sạch, thử đài phun nước.",
                "Chải lông cho mèo mỗi tuần giúp giảm búi lông."));

        assertThat(query).hasSize(OnnxE5Embedder.DIMENSIONS);
        assertThat(dot(query, query)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(dot(query, passages.get(0))).isGreaterThan(dot(query, passages.get(1)));
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private static AiProperties properties(String modelDir) {
        return new AiProperties(false, "http://localhost", "", "model", 6, 8, 100, Duration.ofSeconds(1),
                Duration.ofSeconds(1), modelDir, 0, 0, null);
    }
}
