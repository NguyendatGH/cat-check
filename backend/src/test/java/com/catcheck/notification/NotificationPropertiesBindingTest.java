package com.catcheck.notification;

import com.catcheck.notification.application.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPropertiesBindingTest {

    private NotificationProperties bind(Map<String, Object> overrides) {
        MutablePropertySources sources = new MutablePropertySources();
        if (!overrides.isEmpty()) {
            sources.addFirst(new MapPropertySource("overrides", overrides));
        }
        sources.addLast(applicationYml());
        return new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
                .bind("catcheck.notification", NotificationProperties.class)
                .orElseThrow(() -> new AssertionError("Không bind được catcheck.notification"));
    }

    private PropertySource<?> applicationYml() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"))
                    .getFirst();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void outboxDefaultsMatchPart12() {
        NotificationProperties.Outbox outbox = bind(Map.of()).outbox();
        assertThat(outbox.maxAttempts()).isEqualTo(5);
        assertThat(outbox.emailBatchSize()).isEqualTo(50);
        assertThat(outbox.pushBatchSize()).isEqualTo(100);
    }

    @Test
    void pushIsDisabledWhenNoServiceAccountPathIsSupplied() {
        NotificationProperties.Push push = bind(Map.of()).push();
        assertThat(push.configured()).isFalse();
        assertThat(push.maxDevicesPerUser()).isEqualTo(10);
    }

    @Test
    void pushTurnsOnOnceTheServiceAccountPathIsSupplied() {
        NotificationProperties.Push push = bind(Map.of(
                "catcheck.notification.push.service-account-path", "/run/secrets/firebase-sa.json"))
                .push();
        assertThat(push.configured()).isTrue();
    }

    @Test
    void unrelatedEmailSinkKeyUnderTheSamePrefixDoesNotBreakBinding() {
        NotificationProperties properties = bind(Map.of(
                "catcheck.notification.email-sink", "smtp",
                "catcheck.notification.email-sink-path", "target/dev-mail"));
        assertThat(properties.outbox().maxAttempts()).isEqualTo(5);
    }
}
