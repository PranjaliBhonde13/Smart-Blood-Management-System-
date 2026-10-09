package com.sben.patient;

import com.sben.common.response.ApiResponse;
import com.sben.patient.document.PatientDocumentType;
import com.sben.patient.dto.DocumentView;
import com.sben.patient.dto.PatientRequest;
import com.sben.patient.dto.PatientResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Self-service patient profile and verification documents (see docs/04-API.md §3).
 * Documents are streamed only through authenticated endpoints; the upload
 * directory is never served statically.
 */
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

    /** Upload or replace the identity or blood-group document (track chosen by {@code documentType}). */
    @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PatientResponse> uploadDocument(@AuthenticationPrincipal UserPrincipal me,
                                                       @RequestParam("documentType") PatientDocumentType documentType,
                                                       @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Document submitted for verification",
                service.uploadDocument(me.getId(), documentType, file));
    }

    @GetMapping("/me/documents")
    public ApiResponse<List<DocumentView>> myDocuments(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.myDocumentHistory(me.getId()));
    }

    @GetMapping("/me/documents/{id}/file")
    public ResponseEntity<Resource> myDocumentFile(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return stream(service.myDocumentFile(me.getId(), id));
    }

    /** Stream a private file inline with no-store caching and sniffing disabled. */
    static ResponseEntity<Resource> stream(PatientService.StoredFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.downloadName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }
}
