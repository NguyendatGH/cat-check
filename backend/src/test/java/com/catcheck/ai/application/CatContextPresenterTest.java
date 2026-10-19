package com.catcheck.ai.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatContextPresenterTest {

    @Test
    void freeTextSurveyAnswersNeverReachTheProviderContextButQuestionnaireCodesDo() {
        CatContextPort.CatContext context = new CatContextPort.CatContext(
                UUID.randomUUID(), "Mun", 14, "DSH", null, "MALE", true, null, null, null, null,
                List.of(), List.of(),
                new CatContextPort.SurveySummary("an@example.test", "0987654321",
                        "Bé ăn hạt, gọi tôi khi cần", "1_2_PER_DAY",
                        List.of("STRAINING", "+84 912 345 678", "nhà ở Quận 1")),
                List.of(), null);

        String rendered = CatContextPresenter.renderContext(context);

        assertThat(rendered).doesNotContain("an@example.test", "0987654321", "Bé ăn hạt", "912 345 678", "Quận 1");
        assertThat(rendered).contains("loại cát=-", "tiền sử tiết niệu=-", "chế độ ăn=-",
                "tần suất đi vệ sinh=1_2_PER_DAY", "triệu chứng=STRAINING");
    }
}
