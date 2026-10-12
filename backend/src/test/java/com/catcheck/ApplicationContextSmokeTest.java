package com.catcheck;

import com.catcheck.shared.i18n.I18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(context.containsBean("catController")).isTrue();
        assertThat(context.getBean(com.catcheck.cat.application.CatProfileService.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatNoteRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatHealthSurveyRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.ClinicalSignReportRepository.class)).isNotNull();
        assertThat(context.getBean(com.catcheck.cat.domain.port.CatBreedRepository.class)).isNotNull();
    }

    @Test
    void mvpReferenceDataIsSeeded(@Autowired JdbcTemplate jdbcTemplate) {
        Integer packageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM package_plan WHERE code IN ('MINI', 'DAILY', 'PLUS', 'MULTI', 'CARE_BOX')",
                Integer.class);
        Integer consentPurposeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consent_purpose WHERE code IN ("
                        + "'SERVICE_CORE', 'SCAN_IMAGE_RETAIN', 'ALGO_IMPROVEMENT', "
                        + "'HEALTH_REMINDER_PUSH', 'HEALTH_REMINDER_EMAIL', 'MARKETING_EMAIL', "
                        + "'MARKETING_PUSH', 'PRODUCT_ANALYTICS', 'COOKIE_ANALYTICS', "
                        + "'LOCATION_MAP', 'COMMUNITY_PUBLIC', 'ORDER_FULFILLMENT')",
                Integer.class);
        Integer policyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM policy_version WHERE policy_type IN "
                        + "('TERMS', 'PRIVACY', 'COOKIE', 'MEDICAL_DISCLAIMER') AND locale = 'vi'",
                Integer.class);

        assertThat(packageCount).isEqualTo(5);
        assertThat(consentPurposeCount).isEqualTo(12);
        assertThat(policyCount).isEqualTo(4);
    }
}
