package com.sben.bloodstock.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Payload for a blood bank to set/adjust an inventory line. */
public record BloodStockRequest(
        @NotBlank @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "invalid blood group") String bloodGroup,
        @NotNull @Min(0) Integer unitsAvailable,
        @FutureOrPresent LocalDate expiryDate
) {}
