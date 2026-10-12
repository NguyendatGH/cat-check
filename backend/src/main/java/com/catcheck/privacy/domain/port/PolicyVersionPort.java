package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyVersionPort {

    void publish(PolicyVersion version);

    Optional<PolicyVersion> findCurrent(PolicyType type, String locale, Instant now);

    Optional<PolicyVersion> findById(UUID id);

    List<PolicyVersion> findAllByType(PolicyType type, String locale);

    Optional<PolicyVersion> findByTypeAndVersion(PolicyType type, String version, String locale);

    List<PolicyVersion> findAllForAdmin(PolicyType type, String locale, int limit, int offset);

    long countForAdmin(PolicyType type, String locale);

    int markPublished(UUID id, UUID publishedBy, Instant effectiveFrom);

    int closeEffective(PolicyType type, String locale, Instant effectiveTo, UUID exceptId);
}
