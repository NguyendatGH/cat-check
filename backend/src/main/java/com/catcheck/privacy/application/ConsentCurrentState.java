package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.ConsentStatus;

public record ConsentCurrentState(String purposeCode, ConsentStatus status) {
}
