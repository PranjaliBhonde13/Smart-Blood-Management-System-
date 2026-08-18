package com.sben.bloodrequest;

import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.bloodrequest.dto.CreateBloodRequestRequest;
import com.sben.bloodrequest.dto.UpdateStatusRequest;
import com.sben.common.response.ApiResponse;
import com.sben.security.UserPrincipal;
import com.sben.user.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Blood request endpoints (see docs/04-API.md §4). Access is split by role:
 * patients create/track/cancel their own; hospitals/admin run the queue and
 * drive verification and status transitions.
 */
@RestController
@RequestMapping("/api/v1/blood-requests")
@RequiredArgsConstructor
@Tag(name = "Blood Requests")
public class BloodRequestController {

    private final BloodRequestService service;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<BloodRequestResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                    @Valid @RequestBody CreateBloodRequestRequest req) {
        return ApiResponse.ok("Request created", service.create(me.getId(), req));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<Page<BloodRequestResponse>> mine(@AuthenticationPrincipal UserPrincipal me,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.getMine(me.getId(), pageable));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL','ADMIN','BLOOD_BANK')")
    public ApiResponse<Page<BloodRequestResponse>> queue(@RequestParam(required = false) RequestStatus status,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.queue(status, pageable));
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('DONOR')")
    public ApiResponse<Page<BloodRequestResponse>> openForDonor(@AuthenticationPrincipal UserPrincipal me,
                                                               @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.openForDonor(me.getId(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ApiResponse<BloodRequestResponse> getOne(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal me) {
        Role role = Role.valueOf(me.getRole());
        return ApiResponse.ok(service.getById(id, me.getId(), role));
    }

    @PostMapping("/{id}/report")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<BloodRequestResponse> uploadReport(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserPrincipal me,
                                                          @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Report uploaded", service.uploadReport(id, me.getId(), file));
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasRole('HOSPITAL')")
    public ApiResponse<BloodRequestResponse> verify(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request verified", service.verify(id, me.getId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL','ADMIN','BLOOD_BANK')")
    public ApiResponse<BloodRequestResponse> updateStatus(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateStatusRequest req) {
        return ApiResponse.ok("Status updated", service.changeStatus(id, req.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<BloodRequestResponse> cancel(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request cancelled", service.cancel(id, me.getId()));
    }
}
