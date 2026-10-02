package com.catcheck.media.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link MediaProperties} cho module media.
 *
 * <p>Tách thành class riêng thay vì {@code @Bean} thủ công: {@code @ConfigurationProperties} trên
 * record đã tự gom {@code @DefaultValue}, tạo bean bằng tay sẽ cho ra hai nguồn sự thật và che mất
 * việc binding từ {@code application.yml}.</p>
 *
 * <p>A3 không được sửa {@code CatCheckApplication} nên không thêm
 * {@code @ConfigurationPropertiesScan} ở đây; class này tự nạp là đủ.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfiguration {
}
