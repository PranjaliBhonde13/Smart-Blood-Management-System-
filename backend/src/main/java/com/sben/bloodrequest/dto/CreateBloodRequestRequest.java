package com.sben.bloodrequest.dto;

import com.sben.bloodrequest.BloodComponent;
import com.sben.bloodrequest.RequestFor;
import com.sben.bloodrequest.Urgency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload for a patient raising a new blood request.
 *
 * <p>For {@code SELF} the server uses the requester's saved profile blood group;
 * {@code bloodGroup} may be omitted and, if sent, must match it. For
 * {@code OTHER} the group, recipient name and age are required and the
 * requester's own group is never used. The group's source/verification is
 * always decided by the server.
 */
public record CreateBloodRequestRequest(
        @NotNull(message = "Choose who this request is for") RequestFor requestFor,
        @Pattern(regexp = "^$|^(A|B|AB|O)[+-]$", message = "Choose a valid blood group") String bloodGroup,
        @NotNull(message = "Choose the blood component") BloodComponent component,
        @NotNull(message = "Enter the units required") @Min(value = 1, message = "At least 1 unit")
        @Max(value = 20, message = "At most 20 units per request") Integer unitsRequired,
        @NotNull(message = "Choose the urgency") Urgency urgency,
        @NotNull(message = "Enter when the blood is required") LocalDateTime requiredBy,

        // ---- recipient (OTHER only) ----
        @Size(max = 100, message = "Use 100 characters or fewer") String recipientName,
        @Min(value = 0, message = "Enter a valid age") @Max(value = 120, message = "Enter a valid age") Integer recipientAge,
        @Size(max = 50, message = "Use 50 characters or fewer") String recipientRelationship,

        // ---- where ----
        Long preferredHospitalId,
        @Size(max = 255, message = "Use 255 characters or fewer") String locationNotes,
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90") BigDecimal latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180") BigDecimal longitude
) {}
