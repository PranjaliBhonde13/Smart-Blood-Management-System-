package com.sben.bloodrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Hospital/admin confirmation of a request's blood group. Sending the same group
 * confirms it; a different group corrects it (a reason is then required). Both
 * are audited.
 */
public record ConfirmBloodGroupRequest(
        @NotBlank(message = "Choose the blood group")
        @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Choose a valid blood group") String bloodGroup,
        @Size(max = 500, message = "Use 500 characters or fewer") String reason
) {}
