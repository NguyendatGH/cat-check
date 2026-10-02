package com.catcheck.identity.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lay IP that cua client (p11 §11.7.1).
 *
 * <p><b>Quan trong: chi tin {@code X-Forwarded-For} khi request den tu reverse proxy
 * tin cay.</b> Header do client tu dien, dung no de tinh rate limit thi attacker doi
 * duoc mot header la khop het gioi han. Vi vay danh sach IP proxy nam trong config
 * ({@code catcheck.rate-limit.trusted-proxies}); rong thi bo qua header hoan toan va
 * dung IP socket.</p>
 */
@Component
public class RequestIpResolver {

    private final Set<InetAddress> trustedProxies;

    public RequestIpResolver(
            @Value("${catcheck.rate-limit.trusted-proxies:}") String trustedProxyList) {
        this.trustedProxies = parse(trustedProxyList);
    }

    private static Set<InetAddress> parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        Set<InetAddress> out = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(entry -> {
                    try {
                        out.add(InetAddress.getByName(entry));
                    } catch (UnknownHostException ex) {
                        // Giu nguyen app khoi dong: mot muc cau hinh sai khong duoc
                        // lam chet app, va "khong tin proxy" la fail-safe an toan.
                        out.add(null);
                    }
                });
        out.remove(null);
        return Set.copyOf(out);
    }

    /**
     * @return IP dang chuoi, hoac {@code null} neu khong xac dinh duoc. Chuoi rong la
     *         gia tri hop le cho rate limit (key rong bi bo qua o
     *         {@link com.catcheck.identity.domain.port.RateLimiter}).
     */
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || !isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return remote;
        }
        // Chuoi "client, proxy1, proxy2" — phan tu dau la client thật.
        String first = forwarded.split(",")[0].trim();
        return first.isEmpty() ? remote : first;
    }

    /** Tra ve {@code true} neu request den tu proxy nam trong danh sach tin cay. */
    public boolean isTrustedProxy(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank() || trustedProxies.isEmpty()) {
            return false;
        }
        try {
            return trustedProxies.contains(InetAddress.getByName(remoteAddress));
        } catch (UnknownHostException ex) {
            // Remote address khong parse duoc thi coi nhu khong tin: dung chinh no.
            return false;
        }
    }
}
