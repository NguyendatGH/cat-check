package com.catcheck.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * {@link JdbcTemplate} tu doi {@link Instant} sang {@link OffsetDateTime} cho MOI tham so bind.
 *
 * <p><b>Bug that da sua — lop bug, khong phai mot cho:</b> driver PostgreSQL khong tu suy duoc
 * kieu SQL cho {@code java.time.Instant}, nem
 * {@code Can't infer the SQL type to use for an instance of java.time.Instant} ngay luc chay
 * (khong phai luc compile, nen tsc/mvn deu xanh). Da gap that o it nhat 5 endpoint khac nhau:
 * {@code POST /auth/register} (ghi consent), {@code GET /privacy/consents/history},
 * {@code GET /privacy/requests}, {@code POST /policies/{code}/acknowledge}, va truoc do la
 * {@code JdbcPolicyVersionAdapter#findCurrent}. Quet ca repo con thay ~40 cho bind
 * {@code Instant} tho nam rai rac trong {@code identity}, {@code privacy}, {@code credit},
 * {@code scan} — vá tung cho vua de sot vua chac chan tai dien khi viet adapter moi.</p>
 *
 * <p>Sua mot lan tai day: {@link JdbcTemplate#newArgPreparedStatementSetter} la diem mo rong
 * co san cua Spring cho dung viec nay, va moi phuong thuc nhan {@code Object... args}
 * ({@code update}, {@code query}, {@code queryForObject}...) deu di qua no. Dung {@link OffsetDateTime} chu KHONG phai
 * {@code java.sql.Timestamp}: R13 (ArchUnit) cam cac kieu ngay gio legacy, va driver
 * PostgreSQL ho tro {@code OffsetDateTime} native. Nho vay cac adapter khong con phai tu boc
 * {@code Timestamp.from(...)} nua — bind thang {@code Instant} la duoc.</p>
 *
 * <p>Khong dat {@code spring.jdbc.template.*} nao trong {@code application*.yml} nen dinh nghia
 * bean nay khong lam mat cau hinh auto-config nao.</p>
 */
@Configuration
public class JdbcTemplateConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource) {
            @Override
            protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
                return super.newArgPreparedStatementSetter(convertInstants(args));
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
