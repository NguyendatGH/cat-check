package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.RetentionPolicy;

import java.time.Instant;

/** Cổng đếm read-only cho L58; không được mutate bảng mục tiêu. */
public interface RetentionDryRunPort {

    long countExpired(RetentionPolicy policy, Instant cutoff);
}
