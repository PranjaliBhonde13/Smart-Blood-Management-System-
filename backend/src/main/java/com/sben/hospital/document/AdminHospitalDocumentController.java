package com.sben.hospital.document;

import com.sben.common.response.ApiResponse;
import com.sben.hospital.document.dto.HospitalDocumentViews.*;
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

/**
 * Administrator review of hospital documents. ADMIN-only; the reviewing
 * admin's id always comes from the authenticated principal.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin — Hospital Documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHospitalDocumentController {

    private final HospitalDocumentReviewService reviewService;
    private final HospitalDocumentService documentService;

    /** Hospital details, verification status, current documents, full history and requests. */
    @GetMapping("/hospitals/{id}/documents")
    public ApiResponse<HospitalReview> review(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.review(id));
    }

    @GetMapping("/hospital-documents/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        return HospitalDocumentController.stream(documentService.fileForAdmin(id), download);
    }

    @PatchMapping("/hospital-documents/{id}/approve")
    public ApiResponse<HospitalReview> approve(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me,
                                               @Valid @RequestBody(required = false) ApproveDocumentRequest body) {
        return ApiResponse.ok("Document approved", reviewService.approve(id, me.getId(), body == null ? null : body.remarks()));
    }

    @PatchMapping("/hospital-documents/{id}/reject")
    public ApiResponse<HospitalReview> reject(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me,
                                              @Valid @RequestBody RejectDocumentRequest body) {
        return ApiResponse.ok("Document rejected",
                reviewService.reject(id, me.getId(), body.remarks(), Boolean.TRUE.equals(body.requestCorrection())));
    }

    @PostMapping("/hospitals/{id}/document-requests")
    public ApiResponse<HospitalReview> requestDocument(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me,
                                                       @Valid @RequestBody CreateDocumentRequest body) {
        return ApiResponse.ok("Document requested", reviewService.requestDocument(id, me.getId(), body));
    }

    @PatchMapping("/hospital-document-requests/{id}/cancel")
    public ApiResponse<HospitalReview> cancelRequest(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request withdrawn", reviewService.cancelRequest(id, me.getId()));
    }

    // ---------- requirement rules ----------

    @GetMapping("/hospital-document-requirements")
    public ApiResponse<List<RequirementConfig>> requirements() {
        return ApiResponse.ok(reviewService.requirements());
    }

    @PostMapping("/hospital-document-requirements")
    public ApiResponse<RequirementConfig> createRequirement(@Valid @RequestBody CreateRequirementRequest body) {
        return ApiResponse.ok("Requirement added", reviewService.createRequirement(body));
    }

    @PatchMapping("/hospital-document-requirements/{id}")
    public ApiResponse<RequirementConfig> updateRequirement(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateRequirementRequest body) {
        return ApiResponse.ok("Requirement updated", reviewService.updateRequirement(id, body));
    }
}
