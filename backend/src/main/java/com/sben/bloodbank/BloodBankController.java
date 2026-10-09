package com.sben.bloodbank;

import com.sben.bloodbank.dto.BloodBankRequest;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.common.response.ApiResponse;
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

/**
 * Self-service blood bank profile endpoints (see docs/04-API.md §3). Files are
 * streamed only through these authenticated endpoints — the upload directory is
 * never served statically.
 */
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

    @PostMapping(value = "/me/license-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BloodBankResponse> uploadLicense(@AuthenticationPrincipal UserPrincipal me,
                                                        @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("License submitted for verification", service.uploadLicenseDocument(me.getId(), file));
    }

    @GetMapping("/me/license-document")
    public ResponseEntity<Resource> myLicense(@AuthenticationPrincipal UserPrincipal me) {
        return stream(service.myLicenseDocument(me.getId()));
    }

    @PostMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BloodBankResponse> uploadLogo(@AuthenticationPrincipal UserPrincipal me,
                                                     @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Logo updated", service.uploadLogo(me.getId(), file));
    }

    @DeleteMapping("/me/logo")
    public ApiResponse<BloodBankResponse> removeLogo(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Logo removed", service.removeLogo(me.getId()));
    }

    @GetMapping("/me/logo")
    public ResponseEntity<Resource> myLogo(@AuthenticationPrincipal UserPrincipal me) {
        return stream(service.myLogo(me.getId()));
    }

    /** Stream a private file inline with no-store caching and sniffing disabled. */
    public static ResponseEntity<Resource> stream(BloodBankService.StoredFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.downloadName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }
}
