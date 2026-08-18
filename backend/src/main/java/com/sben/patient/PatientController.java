package com.sben.patient;

import com.sben.common.response.ApiResponse;
import com.sben.patient.dto.PatientRequest;
import com.sben.patient.dto.PatientResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Self-service patient profile endpoints (see docs/04-API.md §3). */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Patient")
@PreAuthorize("hasRole('PATIENT')")
public class PatientController {

    private final PatientService service;

    @GetMapping("/me")
    public ApiResponse<PatientResponse> getMine(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.getMyProfile(me.getId()));
    }

    @PutMapping("/me")
    public ApiResponse<PatientResponse> upsertMine(@AuthenticationPrincipal UserPrincipal me,
                                                   @Valid @RequestBody PatientRequest req) {
        return ApiResponse.ok("Profile saved", service.upsertMyProfile(me.getId(), req));
    }
}
