package com.riskregister.exception;

/** A well-formed request that violates a domain rule (mapped to HTTP 409). */
public class BusinessRuleException extends RuntimeException {
    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
