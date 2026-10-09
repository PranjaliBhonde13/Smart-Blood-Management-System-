package com.sben.bloodrequest;

import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.bloodrequest.dto.ConfirmBloodGroupRequest;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                                                         @PageableDefault(size = 20) Pageable pageable,
                                                         @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.queue(status, pageable, Role.valueOf(me.getRole())));
    }

    /** Verified hospitals a patient may name as their preferred hospital. */
    @GetMapping("/hospital-options")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<List<BloodRequestService.HospitalOption>> hospitalOptions() {
        return ApiResponse.ok(service.hospitalOptions());
    }

    /** Confirm (same group) or correct (different group + reason) the requested blood group. Audited. */
    @PatchMapping("/{id}/blood-group")
    @PreAuthorize("hasAnyRole('HOSPITAL','ADMIN')")
    public ApiResponse<BloodRequestResponse> confirmBloodGroup(@PathVariable Long id,
                                                               @AuthenticationPrincipal UserPrincipal me,
                                                               @Valid @RequestBody ConfirmBloodGroupRequest req) {
        return ApiResponse.ok("Blood group recorded",
                service.confirmBloodGroup(id, req, me.getId(), Role.valueOf(me.getRole())));
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('DONOR')")
    public ApiResponse<Page<BloodRequestResponse>> openForDonor(@AuthenticationPrincipal UserPrincipal me,
                                                               @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.openForDonor(me.getId(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT','DONOR','HOSPITAL','ADMIN')")
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

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserPrincipal me) throws IOException {
        Path file = service.loadReport(id, me.getId(), Role.valueOf(me.getRole()));
        String type = Files.probeContentType(file);
        return ResponseEntity.ok()
                .contentType(type != null ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"request-" + id + "-report" + extension(file) + "\"")
                .body(new FileSystemResource(file));
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasRole('HOSPITAL')")
    public ApiResponse<BloodRequestResponse> verify(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request verified", service.verify(id, me.getId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL','ADMIN')")
    public ApiResponse<BloodRequestResponse> updateStatus(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserPrincipal me,
                                                          @Valid @RequestBody UpdateStatusRequest req) {
        return ApiResponse.ok("Status updated",
                service.updateStatus(id, req.status(), me.getId(), Role.valueOf(me.getRole())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<BloodRequestResponse> cancel(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request cancelled", service.cancel(id, me.getId()));
    }
}
