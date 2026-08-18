package com.sben.donor.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload to create/update the caller's own donor profile. */
public record DonorRequest(
        @NotBlank @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "invalid blood group") String bloodGroup,
        @Min(18) @Max(65) Integer age,
        @Size(max = 50) String city,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @PastOrPresent LocalDate lastDonationDate
) {}
