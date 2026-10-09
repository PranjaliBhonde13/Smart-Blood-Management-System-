package com.sben.admin;

import com.sben.admin.dto.AdminUserResponse;
import com.sben.admin.dto.AdminViews.*;
import com.sben.admin.dto.DashboardStats;
import com.sben.admin.dto.PendingVerifications;
import com.sben.audit.AuditService;
import com.sben.audit.dto.AuditLogResponse;
import com.sben.bloodbank.BloodBankController;
import com.sben.bloodbank.BloodBankService;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodrequest.Urgency;
import com.sben.common.response.ApiResponse;
import com.sben.hospital.dto.HospitalResponse;
import com.sben.security.UserPrincipal;
import com.sben.user.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Admin control plane (see docs/04-API.md §9). Entirely ADMIN-only. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AdminUserService userService;
    private final AdminRequestService requestService;
    private final AdminInsightsService insightsService;
    private final BroadcastService broadcastService;
    private final AuditService auditService;
    private final BloodBankService bloodBankService;

    // ---------- dashboard ----------

    @GetMapping("/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    // ---------- users ----------

    /** Users with optional role, status (ACTIVE / SUSPENDED) and free-text search. */
    @GetMapping("/users")
    public ApiResponse<Page<AdminUserResponse>> users(@RequestParam(required = false) Role role,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String q,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        Boolean enabled = "ACTIVE".equalsIgnoreCase(status) ? Boolean.TRUE
                : "SUSPENDED".equalsIgnoreCase(status) ? Boolean.FALSE : null;
        return ApiResponse.ok(adminService.searchUsers(role, enabled, q, pageable));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserDetail> user(@PathVariable Long id) {
        return ApiResponse.ok(userService.detail(id));
    }

    @PatchMapping("/users/{id}/enable")
    public ApiResponse<AdminUserResponse> enable(@PathVariable Long id,
                                                 @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("User enabled", adminService.setEnabled(id, true, me.getId()));
    }

    /** Suspend an account; the optional reason is shown to the user at sign-in. */
    @PatchMapping("/users/{id}/disable")
    public ApiResponse<AdminUserResponse> disable(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal me,
                                                  @Valid @RequestBody(required = false) SuspendRequest body) {
        return ApiResponse.ok("User disabled",
                adminService.setEnabled(id, false, me.getId(), body == null ? null : body.reason()));
    }

    @PostMapping("/users/{id}/logout")
    public ApiResponse<Void> forceLogout(@PathVariable Long id) {
        userService.forceLogout(id);
        return ApiResponse.ok("User signed out everywhere", null);
    }

    @PostMapping("/users/{id}/password-reset")
    public ApiResponse<Void> passwordReset(@PathVariable Long id) {
        userService.sendPasswordReset(id);
        return ApiResponse.ok("Password reset code sent", null);
    }

    @PostMapping("/admins")
    public ApiResponse<AdminUserResponse> createAdmin(@Valid @RequestBody CreateAdminRequest req) {
        return ApiResponse.ok("Administrator created", userService.createAdmin(req));
    }

    // ---------- verifications ----------

    @GetMapping("/verifications")
    public ApiResponse<PendingVerifications> pending() {
        return ApiResponse.ok(adminService.pendingVerifications());
    }

    /** Refused (422) until all mandatory hospital documents are approved — see AdminHospitalDocumentController. */
    @PatchMapping("/hospitals/{id}/verify")
    public ApiResponse<HospitalResponse> verifyHospital(@PathVariable Long id,
                                                        @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Hospital verified", adminService.verifyHospital(id, me.getId()));
    }

    @PatchMapping("/hospitals/{id}/reject")
    public ApiResponse<HospitalResponse> rejectHospital(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return ApiResponse.ok("Hospital verification rejected", adminService.rejectHospital(id, req.reason()));
    }

    @PatchMapping("/blood-banks/{id}/verify")
    public ApiResponse<BloodBankResponse> verifyBloodBank(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok("Blood bank verified", adminService.verifyBloodBank(id, me.getId()));
    }

    /** The submitted license certificate, for review. Admin-only (class-level @PreAuthorize). */
    @GetMapping("/blood-banks/{id}/license-document")
    public ResponseEntity<Resource> bloodBankLicense(@PathVariable Long id) {
        return BloodBankController.stream(bloodBankService.licenseDocumentForAdmin(id));
    }

    @PatchMapping("/blood-banks/{id}/reject")
    public ApiResponse<BloodBankResponse> rejectBloodBank(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return ApiResponse.ok("Blood bank verification rejected", adminService.rejectBloodBank(id, req.reason()));
    }

    // ---------- requests ----------

    @GetMapping("/requests")
    public ApiResponse<Page<RequestRow>> requests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Urgency urgency,
            @RequestParam(required = false) String bloodGroup,
            @RequestParam(required = false) Long hospitalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "requestDate", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(requestService.search(status, urgency, bloodGroup, hospitalId, from, to, pageable));
    }

    @GetMapping("/requests/stuck")
    public ApiResponse<List<RequestRow>> stuck() {
        return ApiResponse.ok(requestService.stuck());
    }

    @PostMapping("/requests/{id}/escalate")
    public ApiResponse<EscalationResult> escalate(@PathVariable Long id) {
        return ApiResponse.ok("Request escalated", requestService.escalate(id));
    }

    // ---------- insights ----------

    @GetMapping("/inventory")
    public ApiResponse<NetworkInventory> inventory() {
        return ApiResponse.ok(insightsService.inventory());
    }

    @GetMapping("/analytics")
    public ApiResponse<Analytics> analytics(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(insightsService.analytics(days));
    }

    // ---------- broadcast ----------

    @PostMapping("/broadcast")
    public ApiResponse<BroadcastResult> broadcast(@Valid @RequestBody BroadcastRequest req) {
        BroadcastResult result = broadcastService.broadcast(req);
        return ApiResponse.ok(result.sent() ? "Broadcast sent" : "Audience preview", result);
    }

    // ---------- audit ----------

    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        return ApiResponse.ok(auditService.search(action, actorId, entityType, from, to, pageable)
                .map(AuditLogResponse::from));
    }

    @GetMapping("/audit-logs/actions")
    public ApiResponse<List<String>> auditActions() {
        return ApiResponse.ok(auditService.actions());
    }

    @GetMapping("/audit-logs/export")
    public ResponseEntity<String> exportAudit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(auditService.exportCsv(action, actorId, entityType, from, to));
    }
}
