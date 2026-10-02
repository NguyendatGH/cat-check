package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.RateLimitRule;

/**
 * Dem gioi han theo quy tac (p11 §11.7.2).
 *
 * <p>La CONG chu khong phai dich vu: Bucket4j nam o {@code ..infrastructure..} nen
 * {@code ..application..} khong duoc import no (R2).</p>
 */
public interface RateLimiter {

    /**
     * Tieu mot don vi trong thung {@code rule}.
     *
     * @param subject khoa theo {@link RateLimitRule#keyKind()} cua quy tac;
     *                {@code null} duoc phep khi caller khong xac dinh duoc (thi bo
     *                qua quy tac thay vi nem — endpoint van duoc bao ve boi quy tac IP)
     * @return {@code true} neu duoc phep; {@code false} neu het han
     */
    boolean tryConsume(RateLimitRule rule, String subject);

    /**
     * So giay con phai cho truoc khi thung co them 1 don vi.
     *
     * @return {@code 0} neu khong bi chan, {@code > 0} neu dang chan. Dung cho
     *         header {@code Retry-After}.
     */
    long secondsUntilRefill(RateLimitRule rule, String subject);

    /**
     * Cho phep tra ve {@code 429} som nhat co the.
     *
     * <p>Tra ve {@code null} neu khong bi chan.</p>
     */
    default RetryAfter rejectIfLimited(RateLimitRule rule, String subject) {
        return tryConsume(rule, subject) ? null : new RetryAfter(secondsUntilRefill(rule, subject));
    }

    /** So giay client phai cho lai truoc khi thu lai. */
    record RetryAfter(long seconds) {

        /** Header {@code Retry-After} phai la so giay nguyen, toi thieu 1. */
        public long headerSeconds() {
            return Math.max(1, seconds);
        }
    }
}
