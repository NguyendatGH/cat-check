package com.catcheck.cat.infrastructure;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatErrorCodeConfiguration {

    @Bean
    ErrorCode catNotFound() {
        return CatErrorCode.CAT_NOT_FOUND;
    }

    @Bean
    ErrorCode catProfileLimitReached() {
        return CatErrorCode.CAT_PROFILE_LIMIT_REACHED;
    }

    @Bean
    ErrorCode catHardLimitReached() {
        return CatErrorCode.CAT_HARD_LIMIT_REACHED;
    }

    @Bean
    ErrorCode catAlreadyDeleted() {
        return CatErrorCode.CAT_ALREADY_DELETED;
    }

    @Bean
    ErrorCode catAlreadyArchived() {
        return CatErrorCode.CAT_ALREADY_ARCHIVED;
    }

    @Bean
    ErrorCode catBreedUnknown() {
        return CatErrorCode.CAT_BREED_UNKNOWN;
    }

    @Bean
    ErrorCode catAgeConflict() {
        return CatErrorCode.CAT_AGE_CONFLICT;
    }

    @Bean
    ErrorCode surveyVersionUnsupported() {
        return CatErrorCode.SURVEY_VERSION_UNSUPPORTED;
    }

    @Bean
    ErrorCode catNoteNotFound() {
        return CatErrorCode.CAT_NOTE_NOT_FOUND;
    }

    @Bean
    ErrorCode catNoteNotEditable() {
        return CatErrorCode.CAT_NOTE_NOT_EDITABLE;
    }

    @Bean
    ErrorCode catArchived() {
        return CatErrorCode.CAT_ARCHIVED;
    }

    @Bean
    ErrorCode catAlreadyPrimary() {
        return CatErrorCode.CAT_ALREADY_PRIMARY;
    }

    @Bean
    ErrorCode catPrimaryRequiresActive() {
        return CatErrorCode.CAT_PRIMARY_REQUIRES_ACTIVE;
    }

    @Bean
    ErrorCode clinicalSignInvalid() {
        return CatErrorCode.CLINICAL_SIGN_INVALID;
    }

    @Bean
    ErrorCode surveyNotFound() {
        return CatErrorCode.SURVEY_NOT_FOUND;
    }

    @Bean
    ErrorCode avatarNotFound() {
        return CatErrorCode.AVATAR_NOT_FOUND;
    }

    @Bean
    ErrorCode sortFieldNotAllowed() {
        return CatErrorCode.SORT_FIELD_NOT_ALLOWED;
    }
}
