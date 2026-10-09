package com.sben.patient;

import com.sben.common.response.ApiResponse;
import com.sben.patient.document.ReviewDocumentRequest;
import com.sben.patient.dto.PatientResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin review of patient identity and blood-group documents. Entirely ADMIN-only. */
@RestController
@RequestMapping("/api/v1/admin/patient-verifications")
@RequiredArgsConstructor
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPatientVerificationController {

    private final PatientVerificationService service;

    @GetMapping
    public ApiResponse<List<PatientResponse>> queue() {
        return ApiResponse.ok(service.queue());
    }

    @GetMapping("/patients/{patientId}")
    public ApiResponse<PatientVerificationService.PatientReview> detail(@PathVariable Long patientId) {
        return ApiResponse.ok(service.detail(patientId));
    }

    @GetMapping("/documents/{documentId}/file")
    public ResponseEntity<Resource> file(@PathVariable Long documentId) {
        return PatientController.stream(service.file(documentId));
    }

    @PatchMapping("/documents/{documentId}/review")
    public ApiResponse<PatientVerificationService.PatientReview> review(@PathVariable Long documentId,
                                                                       @Valid @RequestBody ReviewDocumentRequest req,
                                                                       @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Review saved", service.review(documentId, req, me.getId()));
    }
}
