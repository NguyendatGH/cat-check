package com.catcheck.identity.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Nap danh sach mat khau pho bien <b>mot lan luc khoi dong</b> (p11 §11.3.5:
 * "danh sach tinh, nap vao bo nho luc khoi dong, so khop sau khi lowercase va bo
 * khoang trang").
 *
 * <p>Thu tu uu tien:</p>
 * <ol>
 *   <li>{@value #OVERRIDE_RESOURCE} tren classpath — danh sach day do do ops dua vao
 *       (khi p11 OQ-3 duoc chot). <b>Uu tien cao nhat</b>, dung de <b>thay</b>, khong
 *       phai doi mot dong code nao.</li>
 *   <li>{@link CommonPasswordBaseline} — duong co so ma A1 ghi san.</li>
 * </ol>
 */
final class CommonPasswordListLoader {

    private static final Logger log = LoggerFactory.getLogger(CommonPasswordListLoader.class);

    /** Mot gia tri moi dong; {@code #} va dong rong bi bo qua. */
    static final String OVERRIDE_RESOURCE = "/common-passwords.txt";

    private CommonPasswordListLoader() {
    }

    static Set<String> load(int maxAgeDays) {
        Set<String> overridden = read(OVERRIDE_RESOURCE);
        Set<String> words = overridden.isEmpty() ? CommonPasswordBaseline.words() : overridden;

        if (words.isEmpty()) {
            log.warn("Danh sach mat khau pho bien rong. App khoi dong duoc nhung KHONG con "
                    + "lop bao ve danh sach — chi con rang buoc do dai va quy tac cau truc.");
        } else if (overridden.isEmpty()) {
            log.info("Dang dung danh co so ({} muc, p11 OQ-3 chua chot nguon 10k). "
                            + "Dat file {} vao classpath de thay khong can doi code.",
                    words.size(), OVERRIDE_RESOURCE);
        } else {
            log.info("Da nap {} mat khau pho bien tu {}.", words.size(), OVERRIDE_RESOURCE);
        }
        log.debug("Danh sach pho bien tai luc tai. Can review sau {} ngay theo p11 OQ-3.", maxAgeDays);
        return words;
    }

    private static Set<String> read(String resource) {
        try (InputStream in = CommonPasswordListLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Set.of();
            }
            try (Stream<String> lines = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).lines()) {
                return lines
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Khong doc duoc danh sach mat khau " + resource, ex);
        }
    }
}
