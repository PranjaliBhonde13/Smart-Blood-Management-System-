package com.sben.bloodstock;

import com.sben.bloodstock.dto.PublicStockSummary;
import com.sben.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Anonymous, aggregate-only data for the public landing page. */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicStockController {

    private final BloodStockService service;

    /** Network-wide availability per blood group (no bank-level detail). */
    @GetMapping("/stock-summary")
    public ApiResponse<List<PublicStockSummary>> stockSummary() {
        return ApiResponse.ok(service.publicSummary());
    }
}
