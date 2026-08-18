package com.sben.hospital;

import com.sben.common.response.ApiResponse;
import com.sben.hospital.dto.HospitalRequest;
import com.sben.hospital.dto.HospitalResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Self-service hospital profile endpoints (see docs/04-API.md §3). */
@RestController
@RequestMapping("/api/v1/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospital")
@PreAuthorize("hasRole('HOSPITAL')")
public class HospitalController {

    private final HospitalService service;

    @GetMapping("/me")
    public ApiResponse<HospitalResponse> getMine(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.getMyProfile(me.getId()));
    }

    @PutMapping("/me")
    public ApiResponse<HospitalResponse> upsertMine(@AuthenticationPrincipal UserPrincipal me,
                                                    @Valid @RequestBody HospitalRequest req) {
        return ApiResponse.ok("Profile saved", service.upsertMyProfile(me.getId(), req));
    }
}
