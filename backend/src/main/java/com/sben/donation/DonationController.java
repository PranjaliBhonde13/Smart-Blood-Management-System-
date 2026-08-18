package com.sben.donation;

import com.sben.common.response.ApiResponse;
import com.sben.donation.dto.DonationResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Donation endpoints (see docs/04-API.md §7). Routes are keyed by request id so
 * clients act on a request without tracking donation ids directly.
 */
@RestController
@RequestMapping("/api/v1/donations")
@RequiredArgsConstructor
@Tag(name = "Donations")
public class DonationController {

    private final DonationService service;

    @PostMapping("/request/{requestId}/accept")
    @PreAuthorize("hasRole('DONOR')")
    public ApiResponse<DonationResponse> accept(@PathVariable Long requestId,
                                                @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request accepted", service.accept(requestId, me.getId()));
    }

    @PostMapping("/request/{requestId}/reject")
    @PreAuthorize("hasRole('DONOR')")
    public ApiResponse<Void> reject(@PathVariable Long requestId,
                                    @AuthenticationPrincipal UserPrincipal me) {
        service.reject(requestId, me.getId());
        return ApiResponse.ok("Request declined", null);
    }

    @PatchMapping("/request/{requestId}/complete")
    @PreAuthorize("hasAnyRole('HOSPITAL','ADMIN')")
    public ApiResponse<DonationResponse> complete(@PathVariable Long requestId) {
        return ApiResponse.ok("Donation completed", service.complete(requestId));
    }

    @PostMapping("/request/{requestId}/fulfill-from-bank")
    @PreAuthorize("hasRole('BLOOD_BANK')")
    public ApiResponse<DonationResponse> fulfillFromBank(@PathVariable Long requestId,
                                                         @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Request fulfilled from stock", service.fulfillFromBank(requestId, me.getId()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DONOR')")
    public ApiResponse<List<DonationResponse>> mine(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(service.myDonations(me.getId()));
    }

    @GetMapping("/request/{requestId}")
    @PreAuthorize("hasAnyRole('HOSPITAL','ADMIN')")
    public ApiResponse<List<DonationResponse>> byRequest(@PathVariable Long requestId) {
        return ApiResponse.ok(service.byRequest(requestId));
    }
}
