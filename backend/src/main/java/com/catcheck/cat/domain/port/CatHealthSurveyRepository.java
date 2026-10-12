package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatHealthSurvey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatHealthSurveyRepository {

    Optional<CatHealthSurvey> findLatestByCatId(UUID catId);

    List<CatHealthSurvey> findHistoryByCatId(UUID catId, int limit);

    boolean hasSubmittedByCatId(UUID catId);

    CatHealthSurvey save(CatHealthSurvey survey);
}
