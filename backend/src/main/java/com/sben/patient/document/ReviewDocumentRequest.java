package com.sben.patient.document;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin decision on one patient document.
 *
 * @param remarks             required for REJECT and REQUEST_CHANGES (shown to the patient)
 * @param confirmedBloodGroup required to APPROVE a blood-group document: the group the
 *                            reviewer read on the document itself
 */
public record ReviewDocumentRequest(
        @NotNull(message = "Choose a decision") Decision decision,
        @Size(max = 1000, message = "Use 1000 characters or fewer") String remarks,
        @Pattern(regexp = "^$|^(A|B|AB|O)[+-]$", message = "Choose a valid blood group") String confirmedBloodGroup
) {
    public enum Decision { APPROVE, REJECT, REQUEST_CHANGES }
}
