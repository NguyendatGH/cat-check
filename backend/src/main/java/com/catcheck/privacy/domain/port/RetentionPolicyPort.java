package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.RetentionPolicy;

import java.util.List;
import java.util.Optional;

public interface RetentionPolicyPort {

    List<RetentionPolicy> findAll();

    Optional<RetentionPolicy> findByCode(String code);

    void save(RetentionPolicy policy);
}
