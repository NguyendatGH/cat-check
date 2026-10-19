package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.ConsentPurpose;

import java.util.List;
import java.util.Optional;

public interface ConsentPurposePort {

    List<ConsentPurpose> findAllActive();

    Optional<ConsentPurpose> findByCode(String purposeCode);
}
