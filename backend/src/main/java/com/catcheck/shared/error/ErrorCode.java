package com.catcheck.shared.error;

import org.springframework.http.HttpStatus;

import java.net.URI;

public interface ErrorCode {

    String code();

    HttpStatus status();

    URI typeUri();
}
