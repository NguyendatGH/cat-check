package com.catcheck.shop.infrastructure.payment;

import com.catcheck.shop.application.PayOsProperties;
import com.catcheck.shop.domain.port.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PayOsProperties.class)
public class PaymentGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfiguration.class);

    @Bean
    public PaymentGateway paymentGateway(PayOsProperties properties) {
        if (!properties.configured()) {
            log.info("PayOS: TẮT (thiếu catcheck.shop.payos.client-id/api-key/checksum-key).");
            return new DisabledPaymentGateway();
        }
        log.info("PayOS: BẬT.");
        return new PayOsPaymentGateway(new PayOS(ClientOptions.builder()
                .clientId(properties.clientId())
                .apiKey(properties.apiKey())
                .checksumKey(properties.checksumKey())
                .timeoutMs(properties.timeoutMs())
                .maxRetries(properties.maxRetries())
                .build()));
    }
}
