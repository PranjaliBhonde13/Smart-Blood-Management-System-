package com.sben.patient.dto;

import jakarta.validation.constraints.*;

/** Payload to create/update the caller's own patient profile. */
public record PatientRequest(
        @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "invalid blood group") String bloodGroup,
        @Min(0) @Max(120) Integer age,
        @Size(max = 10) String gender,
        @Size(max = 255) String address,
        @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "invalid contact") String emergencyContact
) {}
