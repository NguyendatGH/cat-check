package com.catcheck.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.SqlProvider;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Configuration
public class JdbcTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplateConfig.class);

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource) {
            @Override
            protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
                return super.newArgPreparedStatementSetter(convertInstants(args));
            }

            @Override
            public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action) {
                if (log.isDebugEnabled() && psc instanceof SqlProvider sqlProvider) {
                    log.debug("SQL: {}", sqlProvider.getSql());
                }
                return super.execute(psc, action);
            }
        };
    }

    private static Object[] convertInstants(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] converted = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Instant instant) {
                if (converted == null) {
                    converted = args.clone();
                }
                converted[i] = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
            }
        }
        return converted == null ? args : converted;
    }
}
