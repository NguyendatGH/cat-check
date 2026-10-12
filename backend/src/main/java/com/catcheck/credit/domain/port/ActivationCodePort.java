package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
import com.catcheck.credit.domain.ActivationCodeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivationCodePort {

    Optional<ActivationCode> findByCodeHash(String codeHash);

    void insert(ActivationCode code);

    void insertAll(List<ActivationCode> codes);

    boolean markRedeemed(UUID codeId, UUID userId, Instant redeemedAt);

    boolean markVoid(UUID codeId);

    List<ActivationCode> findIssuedCodes(String packageCode, ActivationCodeStatus status, int offset, int limit);

    long countIssuedCodes(String packageCode, ActivationCodeStatus status);

    Optional<ActivationCode> findById(UUID codeId);

    List<ActivationCode> search(ActivationCodeFilter filter, int offset, int limit);

    long count(ActivationCodeFilter filter);

    List<ActivationBatchSummary> listBatches(int offset, int limit);

    long countBatches();

    Optional<ActivationBatchSummary> findBatch(String productionBatch);

    void lockProductionBatch(String productionBatch);

    int markBatchVoid(String productionBatch);

    Optional<ActivationCode> lockById(UUID codeId);
}
