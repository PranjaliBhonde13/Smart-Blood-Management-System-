package com.sben.report;

import com.sben.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Reporting & export endpoints (see docs/04-API.md §10). */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final ReportService service;

    @GetMapping("/requests/summary")
    @PreAuthorize("hasAnyRole('ADMIN','HOSPITAL')")
    public ApiResponse<Map<String, Long>> requests() {
        return ApiResponse.ok(service.requestsSummary());
    }

    @GetMapping("/donations/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Long>> donations() {
        return ApiResponse.ok(service.donationsSummary());
    }

    @GetMapping("/inventory/summary")
    @PreAuthorize("hasAnyRole('ADMIN','BLOOD_BANK')")
    public ApiResponse<Map<String, Long>> inventory() {
        return ApiResponse.ok(service.inventorySummary());
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "requests") String type) {
        // Only 'requests' CSV is supported today; extend per type as needed.
        String csv = service.exportRequestsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + type + "-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
