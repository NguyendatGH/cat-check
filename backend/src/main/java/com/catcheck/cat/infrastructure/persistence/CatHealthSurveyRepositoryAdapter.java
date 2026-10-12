package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatHealthSurvey;
import com.catcheck.cat.domain.port.CatHealthSurveyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CatHealthSurveyRepositoryAdapter implements CatHealthSurveyRepository {

    private final CatHealthSurveyJpaRepository jpaRepository;

    CatHealthSurveyRepositoryAdapter(CatHealthSurveyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CatHealthSurvey> findLatestByCatId(UUID catId) {
        return jpaRepository.findFirstByCatIdOrderByCreatedAtDesc(catId);
    }

    @Override
    public List<CatHealthSurvey> findHistoryByCatId(UUID catId, int limit) {
        return jpaRepository.findHistoryByCatId(catId, PageRequest.of(0, Math.max(limit, 1)));
    }

    @Override
    public boolean hasSubmittedByCatId(UUID catId) {
        return jpaRepository.findFirstByCatIdOrderByCreatedAtDesc(catId)
                .map(survey -> !survey.isSkipped())
                .orElse(false);
    }

    @Override
    public CatHealthSurvey save(CatHealthSurvey survey) {
        return jpaRepository.save(survey);
    }
}
