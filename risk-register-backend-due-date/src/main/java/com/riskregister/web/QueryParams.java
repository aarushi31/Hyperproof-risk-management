package com.riskregister.web;

import com.riskregister.exception.BadRequestException;
import java.util.Arrays;

final class QueryParams {

    private QueryParams() {}

    /** Case-insensitive enum parsing with a helpful 400 message. Blank/null → null (no filter). */
    static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String param) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(raw.trim())) {
                return constant;
            }
        }
        throw new BadRequestException("Invalid value '%s' for query parameter '%s'. Allowed values: %s"
                .formatted(raw, param, Arrays.toString(type.getEnumConstants())));
    }
}
