package com.sben.bloodbank;

import com.sben.bloodbank.dto.BloodBankRequest;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.common.response.ApiResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Self-service blood bank profile endpoints (see docs/04-API.md §3). */
@RestController
@RequestMapping("/api/v1/blood-banks")
@RequiredArgsConstructor
@Tag(name = "Blood Bank")
@PreAuthorize("hasRole('BLOOD_BANK')")
public class BloodBankController {

    private final BloodBankService service;

    @GetMapping("/me")
    public ApiResponse<BloodBankResponse> getMine(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.getMyProfile(me.getId()));
    }

    @PutMapping("/me")
    public ApiResponse<BloodBankResponse> upsertMine(@AuthenticationPrincipal UserPrincipal me,
                                                     @Valid @RequestBody BloodBankRequest req) {
        return ApiResponse.ok("Profile saved", service.upsertMyProfile(me.getId(), req));
    }
}
