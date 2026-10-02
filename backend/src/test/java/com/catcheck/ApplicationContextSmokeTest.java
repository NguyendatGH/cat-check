package com.catcheck;

import com.catcheck.shared.i18n.I18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test khởi động {@code ApplicationContext} THẬT (không phải
 * {@code ApplicationModules.of(...).verify()} tĩnh của {@code ModularityTests}, cũng không phải
 * {@code mvn compile}) — đây là lỗ hổng đã được xác nhận: toàn bộ repo trước đây KHÔNG có test nào
 * thật sự gọi {@code SpringApplication.run}, nên thiếu bean (ví dụ persistence adapter của module
 * {@code cat}) không bị phát hiện dù {@code mvn compile} và mọi {@code *RuleTests} đều xanh —
 * compile chỉ cần interface, Spring cần implementation.
 *
 * <p>Dùng PostgreSQL thật qua Testcontainers (không H2) — {@code research-integrations.md} §Testcontainers
 * giải thích lý do: H2 khác hành vi {@code jsonb}, {@code uuidv7()}, partial index nên "test xanh mà
 * prod đỏ". Docker image ghim đúng {@code postgres:18.6-trixie} (research-integrations.md, không tự
 * chọn "bản mới nhất").</p>
 *
 * <p><b>{@code @DynamicPropertySource}, không phải {@code @ServiceConnection}.</b>
 * {@code application-test.yml} gợi ý cả hai cách; {@code @ServiceConnection} cần dependency
 * {@code org.springframework.boot:spring-boot-testcontainers} — kiểm tra {@code pom.xml} (đã pin
 * 91 version, KHÔNG được thêm) xác nhận dependency đó KHÔNG có trong POM, nên dùng
 * {@code @DynamicPropertySource} (chỉ cần {@code spring-boot-starter-test} + hai artifact
 * {@code testcontainers-postgresql}/{@code testcontainers-junit-jupiter} đã có sẵn từ M0).</p>
 *
 * <p><b>Nếu test này ĐỎ vì lý do NGOÀI tầm kiểm soát của module {@code cat}</b> (ví dụ: module
 * khác có @RestController phụ thuộc một @Service chưa từng được đăng ký bean, hoặc SPI của module
 * khác chưa có adapter nào) — đó CHÍNH XÁC là loại lỗ hổng mà test này được viết ra để bắt, chỉ
 * là ở một module khác. Xem {@code docs/handovers/A3-backend-fix.md} để biết hiện trạng đã xác
 * nhận tại thời điểm agent này chạy.</p>
 *
 * <p><b>{@code @EnableConfigurationProperties(I18nProperties.class)} là một workaround CHỈ Ở TẦNG
 * TEST, không phải sửa lỗi thật.</b> Không có annotation này, context KHÔNG khởi động được ở BẤT
 * KỲ module nào (không riêng cat) vì {@code shared.i18n.LocaleConfig} cần bean
 * {@code I18nProperties} nhưng KHÔNG NƠI NÀO trong {@code shared/**} (hay
 * {@code CatCheckApplication}) có {@code @EnableConfigurationProperties}/
 * {@code @ConfigurationPropertiesScan} đăng ký nó — nghĩa là ỨNG DỤNG THẬT (không chỉ test) hiện
 * KHÔNG khởi động được, kể từ M0, không do agent này gây ra. Vì {@code shared/**} và
 * {@code CatCheckApplication.java} đều ngoài phạm vi sửa của agent này, annotation ở đây chỉ đủ để
 * bài test tự nó đi tiếp và kiểm tra phần còn lại — KHÔNG thay thế việc W3 phải thêm
 * {@code @EnableConfigurationProperties(I18nProperties.class)} thật vào
 * {@code shared.i18n.LocaleConfig} (hoặc {@code @ConfigurationPropertiesScan} vào
 * {@code CatCheckApplication}). Xem {@code docs/handovers/A3-backend-fix.md}.</p>
 */
@SpringBootTest
@EnableConfigurationProperties(I18nProperties.class)
@ActiveProfiles("test")
@Testcontainers
class ApplicationContextSmokeTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
        // Xác nhận cụ thể rằng lỗ hổng ban đầu (persistence adapter rỗng + controller vắng mặt
        // của module cat) đã được vá — không chỉ "context load được nhờ may mắn thứ tự bean".
        // KHÔNG tham chiếu com.catcheck.cat.api.CatController.class ở đây: ArchUnit R5
        // (ControllerDependencyRuleTests) cấm bất kỳ lớp nào phụ thuộc một *Controller ngoại trừ
        // chính test riêng của nó (ví dụ CatControllerTest) — đã xác nhận bằng mvn test thật, R5
        // đỏ khi lớp này còn gọi context.getBean(CatController.class). Tên bean mặc định của
        // Spring (camelCase tên lớp) là đủ để xác nhận nó tồn tại mà không cần tham chiếu Class.
        assertThat(context.containsBean("catController")).isTrue();
        assertThat(context.getBean(com.catcheck.cat.application.CatProfileService.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatNoteRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatHealthSurveyRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.ClinicalSignReportRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatBreedRepository.class)).isNotNull();
    }
}
