package com.sben.bloodrequest.dto;

import com.sben.bloodrequest.RequestStatus;
import jakarta.validation.constraints.NotNull;

/** Payload for a guarded status transition (hospital/admin). */
public record UpdateStatusRequest(
        @NotNull RequestStatus status
) {}
