package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ReferenceCardLayout;
import com.catcheck.colorchart.domain.port.ReferenceCardLayoutRepository;
import com.catcheck.scan.domain.color.CardLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử {@link CardLayoutCatalogAdapter} — map JSON {@code reference_card_layout.layout} (p4 D8)
 * sang {@link CardLayout}. Dữ liệu JSON dưới đây CHÉP ĐÚNG cấu trúc thật đọc bằng
 * {@code psql ... select layout from reference_card_layout where code='CC-CARD'} (rect/ref_lab/role
 * của 5 patch NEUTRAL + 1 COLOR + 1 PH_LEVEL để kiểm cả việc lọc role) — không phải mô phỏng, đây
 * CHÍNH LÀ hình dạng JSON thật, chỉ rút gọn danh sách patch cho test ngắn gọn.
 *
 * <p>Cố tình trộn {@code Integer}/{@code Double} trong map test (giống Jackson deserialize JSONB
 * thật — số nguyên ra {@code Integer}, số thập phân ra {@code Double}) để chứng minh adapter dùng
 * {@code Number} chứ không ép kiểu cứng.
 */
class CardLayoutCatalogAdapterTest {

    private static final String CODE = "CC-CARD";

    @Test
    @DisplayName("Parse JSON thật của CC-CARD: canonical size, 5 patch NEUTRAL, lọc đúng role")
    void parsesRealCardJsonShape() {
        Map<String, Object> layout = Map.of(
                "code", CODE,
                "version", 1,
                "canonical_px", Map.of("w", 600, "h", 380.0),
                "patches", List.of(
                        patch("W1", 0.06, 0.12, 0.14, 0.16, "NEUTRAL", 95.494, 0.0, 0.0),
                        patch("G80", 0.22, 0.12, 0.14, 0.16, "NEUTRAL", 82.046, 0.0, 0.0),
                        patch("G50", 0.38, 0.12, 0.14, 0.16, "NEUTRAL", 53.585, 0.0, 0.0),
                        patch("G20", 0.54, 0.12, 0.14, 0.16, "NEUTRAL", 21.247, 0.0, 0.0),
                        patch("BK", 0.70, 0.12, 0.14, 0.16, "NEUTRAL", 5.063, 0.0, 0.0),
                        patch("R", 0.06, 0.34, 0.14, 0.16, "COLOR", 44.673, 52.908, 39.238),
                        patch("PH_6_6", 0.54, 0.56, 0.14, 0.16, "PH_LEVEL", 63.978, -48.542, 39.735)));

        ReferenceCardLayout entity = new ReferenceCardLayout(UUID.randomUUID(), CODE, 1, layout, true,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        ReferenceCardLayoutRepository repo = new FakeRepository(entity);
        CardLayoutCatalogAdapter adapter = new CardLayoutCatalogAdapter(repo);

        Optional<CardLayout> result = adapter.findActiveLayout(CODE);

        assertThat(result).isPresent();
        CardLayout cardLayout = result.get();
        assertThat(cardLayout.code()).isEqualTo(CODE);
        assertThat(cardLayout.version()).isEqualTo(1);
        assertThat(cardLayout.canonicalWidth()).isEqualTo(600);
        assertThat(cardLayout.canonicalHeight()).isEqualTo(380);
        assertThat(cardLayout.patches()).hasSize(7);

        List<CardLayout.Patch> neutral = cardLayout.neutralPatches();
        assertThat(neutral).hasSize(5);
        assertThat(neutral).extracting(CardLayout.Patch::id)
                .containsExactly("W1", "G80", "G50", "G20", "BK");

        CardLayout.Patch w1 = neutral.get(0);
        assertThat(w1.x()).isEqualTo(0.06);
        assertThat(w1.y()).isEqualTo(0.12);
        assertThat(w1.w()).isEqualTo(0.14);
        assertThat(w1.h()).isEqualTo(0.16);
        assertThat(w1.role()).isEqualTo(CardLayout.Role.NEUTRAL);
        assertThat(w1.refLab().l()).isEqualTo(95.494);
        assertThat(w1.refLab().a()).isZero();
        assertThat(w1.refLab().b()).isZero();

        CardLayout.Patch colorPatch = cardLayout.patches().stream()
                .filter(p -> p.id().equals("R")).findFirst().orElseThrow();
        assertThat(colorPatch.role()).isEqualTo(CardLayout.Role.COLOR);
        assertThat(colorPatch.refLab().a()).isEqualTo(52.908);
    }

    @Test
    @DisplayName("Không có layout active cho mã này: rỗng, không ném lỗi")
    void returnsEmptyWhenNoActiveLayout() {
        ReferenceCardLayoutRepository repo = new FakeRepository(null);
        CardLayoutCatalogAdapter adapter = new CardLayoutCatalogAdapter(repo);

        assertThat(adapter.findActiveLayout("UNKNOWN-CODE")).isEmpty();
    }

    private static Map<String, Object> patch(String id, double x, double y, double w, double h,
                                              String role, double l, double a, double b) {
        return Map.of(
                "id", id,
                "rect", List.of(x, y, w, h),
                "role", role,
                "ref_lab", List.of(l, a, b));
    }

    private static final class FakeRepository implements ReferenceCardLayoutRepository {
        private final ReferenceCardLayout entity;

        FakeRepository(ReferenceCardLayout entity) {
            this.entity = entity;
        }

        @Override
        public Optional<ReferenceCardLayout> findById(UUID id) {
            throw new UnsupportedOperationException("khong can cho test nay");
        }

        @Override
        public Optional<ReferenceCardLayout> findActiveByCode(String code) {
            return entity != null && entity.getCode().equals(code) ? Optional.of(entity) : Optional.empty();
        }
    }
}
