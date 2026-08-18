package com.sben.bloodstock;

import com.sben.bloodstock.dto.BloodStockRequest;
import com.sben.bloodstock.dto.BloodStockResponse;
import com.sben.common.response.ApiResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Blood bank inventory endpoints (see docs/04-API.md §5). */
@RestController
@RequestMapping("/api/v1/blood-stock")
@RequiredArgsConstructor
@Tag(name = "Blood Stock")
@PreAuthorize("hasRole('BLOOD_BANK')")
public class BloodStockController {

    private final BloodStockService service;

    @GetMapping("/mine")
    public ApiResponse<List<BloodStockResponse>> mine(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.getMyStock(me.getId()));
    }

    @PutMapping
    public ApiResponse<BloodStockResponse> upsert(@AuthenticationPrincipal UserPrincipal me,
                                                  @Valid @RequestBody BloodStockRequest req) {
        return ApiResponse.ok("Stock updated", service.upsert(me.getId(), req));
    }
}
