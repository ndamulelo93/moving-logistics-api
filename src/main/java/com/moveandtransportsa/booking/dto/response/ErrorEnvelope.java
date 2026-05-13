package com.moveandtransportsa.booking.dto.response;

import java.util.List;

public record ErrorEnvelope(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors
) {
    public record FieldError(
            String field,
            String message
    ) {
    }
}
