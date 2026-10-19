package com.catcheck.shared.error;

public class ExternalServiceException extends CatCheckException {

    private final String serviceName;

    public ExternalServiceException(ErrorCode errorCode, String serviceName, Object... args) {
        super(errorCode, args);
        this.serviceName = serviceName;
    }

    public ExternalServiceException(ErrorCode errorCode, String serviceName, Throwable cause, Object... args) {
        super(errorCode, cause, args);
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
