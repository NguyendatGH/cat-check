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
                        out.add(null);
                    }
                });
        out.remove(null);
        return Set.copyOf(out);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || !isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return remote;
        }
        String first = forwarded.split(",")[0].trim();
        return first.isEmpty() ? remote : first;
    }

    public boolean isTrustedProxy(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank() || trustedProxies.isEmpty()) {
            return false;
        }
        try {
            return trustedProxies.contains(InetAddress.getByName(remoteAddress));
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
