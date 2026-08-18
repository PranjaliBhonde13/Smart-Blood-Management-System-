package com.sben.bloodbank.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BloodBankRequest(
        @NotBlank @Size(max = 100) String bankName,
        @NotBlank @Size(max = 50) String licenseNumber,
        @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "invalid phone") String phone,
        @Size(max = 255) String address,
        @Size(max = 50) String city,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
) {}
