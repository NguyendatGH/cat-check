package com.catcheck.place.api;

import org.springframework.beans.PropertyAccessException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;

final class RedactedBindingErrorProcessor extends DefaultBindingErrorProcessor {

    @Override
    public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
        bindingResult.addError(new FieldError(bindingResult.getObjectName(), ex.getPropertyName(), null, true,
                new String[] {"typeMismatch"}, null, "invalid"));
    }
}
