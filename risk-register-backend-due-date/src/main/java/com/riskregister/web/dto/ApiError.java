package com.riskregister.web.dto;

import java.time.Instant;
import java.util.List;

/** Uniform error body for every 4xx/5xx produced by this API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        List<FieldIssue> fieldErrors) {

    public record FieldIssue(String field, String message) {}
}
