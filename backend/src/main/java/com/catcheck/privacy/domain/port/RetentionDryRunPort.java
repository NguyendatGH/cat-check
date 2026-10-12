package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.RetentionPolicy;

import java.time.Instant;

public interface RetentionDryRunPort {

    long countExpired(RetentionPolicy policy, Instant cutoff);
}
