package com.sben.bloodrequest.dto;

import com.sben.bloodrequest.Urgency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Payload for a patient raising a new blood request. */
public record CreateBloodRequestRequest(
        @NotBlank @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "invalid blood group") String bloodGroup,
        @NotNull @Min(1) @Max(20) Integer unitsRequired,
        @NotNull Urgency urgency,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
) {}
