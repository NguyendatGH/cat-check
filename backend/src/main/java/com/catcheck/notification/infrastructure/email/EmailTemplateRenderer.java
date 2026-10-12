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

    public Optional<RenderedEmail> render(String templateCode, Map<String, ?> variables) {
        String source = load(templateCode);
        if (source == null) {
            return Optional.empty();
        }
        Map<String, Object> values = new HashMap<>(variables);
        values.put("appUrl", appUrl);
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
