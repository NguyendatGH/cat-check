package com.catcheck.notification.infrastructure.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Render email HTML từ {@code classpath:email/<mã template>.html}.
 *
 * <p>Mỗi template có {@code <title>} (tiêu đề thư) và các biến dạng {@code {{tên}}}. Giá trị biến
 * luôn được escape HTML, còn tiêu đề thì không (tiêu đề là header thư, không phải HTML). Biến không
 * có giá trị được thay bằng chuỗi rỗng, không để lại {@code {{...}}} trong thư.</p>
 *
 * <p>Biến {@code appUrl} luôn có sẵn, lấy từ {@code catcheck.notification.app-url}, để template
 * tạo được link tuyệt đối.</p>
 */
@Component
public class EmailTemplateRenderer {

    private static final Pattern TITLE =
            Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Pattern TEMPLATE_CODE = Pattern.compile("[A-Z0-9_]+");

    private final String appUrl;

    public EmailTemplateRenderer(
            @Value("${catcheck.notification.app-url:http://localhost:5173}") String appUrl) {
        this.appUrl = appUrl;
    }

    /** Thư đã render: tiêu đề và thân HTML. Rỗng nếu chưa có template cho mã này. */
    public Optional<RenderedEmail> render(String templateCode, Map<String, ?> variables) {
        String source = load(templateCode);
        if (source == null) {
            return Optional.empty();
        }
        Map<String, Object> values = new HashMap<>(variables);
        values.put("appUrl", appUrl);
        // Tiêu đề là header thư: bỏ xuống dòng để giá trị biến (tên mèo do người dùng nhập) không
        // chèn được header mới.
        String subject = fill(titleOf(source), values, false).replaceAll("[\\r\\n]+", " ").strip();
        return Optional.of(new RenderedEmail(subject, fill(source, values, true)));
    }

    private static String load(String templateCode) {
        if (templateCode == null || !TEMPLATE_CODE.matcher(templateCode).matches()) {
            return null;
        }
        ClassPathResource resource = new ClassPathResource("email/" + templateCode + ".html");
        if (!resource.exists()) {
            return null;
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String titleOf(String source) {
        Matcher matcher = TITLE.matcher(source);
        return matcher.find() ? matcher.group(1).strip() : "CatCheck";
    }

    private static String fill(String text, Map<String, Object> values, boolean escape) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            String replacement = value == null ? "" : String.valueOf(value);
            if (escape) {
                replacement = HtmlUtils.htmlEscape(replacement);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public record RenderedEmail(String subject, String html) { }
}
