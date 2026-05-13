package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/admin/drivers.
 */
public record CreateDriverRequest(
        @NotBlank String fullName,
        @NotBlank @Pattern(regexp = "^(\\+27|0)[0-9]{9}$") String phone,
        @NotBlank String licenceNumber,
        boolean active
) {
}
