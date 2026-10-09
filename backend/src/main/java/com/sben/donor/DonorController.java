package com.sben.donor;

import com.sben.bloodbank.BloodBankController;
import com.sben.common.response.ApiResponse;
import com.sben.donor.dto.DonorRequest;
import com.sben.donor.dto.DonorResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Self-service donor profile + availability toggle (see docs/04-API.md §3). */
@RestController
@RequestMapping("/api/v1/donors")
@RequiredArgsConstructor
@Tag(name = "Donor")
@PreAuthorize("hasRole('DONOR')")
public class DonorController {

    private final DonorService service;

    @GetMapping("/me")
    public ApiResponse<DonorResponse> getMine(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.getMyProfile(me.getId()));
    }

    @PutMapping("/me")
    public ApiResponse<DonorResponse> upsertMine(@AuthenticationPrincipal UserPrincipal me,
                                                 @Valid @RequestBody DonorRequest req) {
        return ApiResponse.ok("Profile saved", service.upsertMyProfile(me.getId(), req));
    }

    @PatchMapping("/me/availability")
    public ApiResponse<DonorResponse> setAvailability(@AuthenticationPrincipal UserPrincipal me,
                                                      @RequestParam Availability value) {
        return ApiResponse.ok("Availability updated", service.setAvailability(me.getId(), value));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DonorResponse> uploadPhoto(@AuthenticationPrincipal UserPrincipal me,
                                                  @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Photo updated", service.uploadPhoto(me.getId(), file));
    }

    @DeleteMapping("/me/photo")
    public ApiResponse<DonorResponse> removePhoto(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Photo removed", service.removePhoto(me.getId()));
    }

    @GetMapping("/me/photo")
    public ResponseEntity<Resource> myPhoto(@AuthenticationPrincipal UserPrincipal me) {
        return BloodBankController.stream(service.myPhoto(me.getId()));
    }
}
