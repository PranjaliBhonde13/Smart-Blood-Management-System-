package com.sben.hospital.document;

import com.sben.common.response.ApiResponse;
import com.sben.hospital.document.dto.HospitalDocumentViews.HospitalDocumentsOverview;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * The signed-in hospital's own documents. Uploads carry no status field: every
 * upload is PENDING until an administrator reviews it. Files are streamed only
 * through these authenticated endpoints; the upload directory is never public.
 */
@RestController
@RequestMapping("/api/v1/hospitals/me/documents")
@RequiredArgsConstructor
@Tag(name = "Hospital Documents")
@PreAuthorize("hasRole('HOSPITAL')")
public class HospitalDocumentController {

    private final HospitalDocumentService service;

    @GetMapping
    public ApiResponse<HospitalDocumentsOverview> overview(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.overview(me.getId()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HospitalDocumentsOverview> upload(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false) HospitalDocumentType documentType,
            @RequestParam(value = "issueDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "replacesDocumentId", required = false) Long replacesDocumentId,
            @RequestParam(value = "requestId", required = false) Long requestId) {
        return ApiResponse.ok("Document submitted for verification",
                service.upload(me.getId(), file, documentType, issueDate, expiryDate, replacesDocumentId, requestId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<HospitalDocumentsOverview> remove(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return ApiResponse.ok("Document removed", service.remove(me.getId(), id));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean download) {
        return stream(service.myFile(me.getId(), id), download);
    }

    /** Stream a private document with no-store caching and content sniffing disabled. */
    public static ResponseEntity<Resource> stream(HospitalDocumentService.DocumentFile file, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file.path()));
    }
}
