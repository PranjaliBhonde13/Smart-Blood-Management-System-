package com.sben.admin;

import com.sben.admin.dto.AdminUserResponse;
import com.sben.admin.dto.DashboardStats;
import com.sben.admin.dto.PendingVerifications;
import com.sben.audit.AuditService;
import com.sben.audit.dto.AuditLogResponse;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.common.response.ApiResponse;
import com.sben.hospital.dto.HospitalResponse;
import com.sben.user.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin control plane (see docs/04-API.md §9). Entirely ADMIN-only. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/users")
    public ApiResponse<Page<AdminUserResponse>> users(@RequestParam(required = false) Role role,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(adminService.listUsers(role, pageable));
    }

    @PatchMapping("/users/{id}/enable")
    public ApiResponse<AdminUserResponse> enable(@PathVariable Long id) {
        return ApiResponse.ok("User enabled", adminService.setEnabled(id, true));
    }

    @PatchMapping("/users/{id}/disable")
    public ApiResponse<AdminUserResponse> disable(@PathVariable Long id) {
        return ApiResponse.ok("User disabled", adminService.setEnabled(id, false));
    }

    @GetMapping("/verifications")
    public ApiResponse<PendingVerifications> pending() {
        return ApiResponse.ok(adminService.pendingVerifications());
    }

    @PatchMapping("/hospitals/{id}/verify")
    public ApiResponse<HospitalResponse> verifyHospital(@PathVariable Long id) {
        return ApiResponse.ok("Hospital verified", adminService.verifyHospital(id));
    }

    @PatchMapping("/blood-banks/{id}/verify")
    public ApiResponse<BloodBankResponse> verifyBloodBank(@PathVariable Long id) {
        return ApiResponse.ok("Blood bank verified", adminService.verifyBloodBank(id));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLogResponse>> auditLogs(@PageableDefault(size = 30) Pageable pageable) {
        return ApiResponse.ok(auditService.list(pageable).map(AuditLogResponse::from));
    }
}
