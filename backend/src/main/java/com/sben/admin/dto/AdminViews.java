package com.sben.admin.dto;

import com.sben.audit.dto.AuditLogResponse;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodrequest.Urgency;
import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.donation.dto.DonationResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request/response shapes for the admin control plane. */
public final class AdminViews {

    private AdminViews() {}

    // ---------- users ----------

    /** Everything an admin needs about one user on a single page. */
    public record UserDetail(
            AdminUserResponse user,
            Map<String, Object> profile,
            Map<String, Long> stats,
            List<BloodRequestResponse> recentRequests,
            List<DonationResponse> recentDonations,
            List<AuditLogResponse> activity
    ) {}

    public record SuspendRequest(@Size(max = 500) String reason) {}

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}

    public record CreateAdminRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "invalid phone") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "password must be 8-72 chars") String password
    ) {}

    // ---------- requests ----------

    /** One row of the admin request monitor, with names resolved and SLA state. */
    public record RequestRow(
            Long id, String bloodGroup, Integer unitsRequired, Urgency urgency, RequestStatus status,
            LocalDateTime requestDate, LocalDateTime lastChange,
            Long patientUserId, String patientName, String patientPhone,
            Long hospitalId, String hospitalName,
            boolean hasReport, long minutesInStatus, long slaMinutes, boolean overdue
    ) {}

    public record EscalationResult(Long requestId, int donorsAlerted, int hospitalsAlerted) {}

    // ---------- inventory & donors ----------

    public record BankInventory(
            Long bankId, String bankName, String city, boolean verified,
            Map<String, Integer> units, int totalUnits,
            List<String> lowGroups, int expiringSoonUnits, int expiredUnits,
            LocalDate nextExpiry, LocalDateTime lastUpdated
    ) {}

    public record GroupSupply(String bloodGroup, int units, int banksHolding, String level) {}

    public record DonorPool(String bloodGroup, long registered, long available, long eligibleNow) {}

    public record NetworkInventory(
            List<GroupSupply> groups, List<BankInventory> banks, List<DonorPool> donors,
            int lowStockThreshold, int expiryWarningDays
    ) {}

    // ---------- analytics ----------

    public record DayPoint(LocalDate date, long created, long completed) {}

    public record HospitalPerformance(
            Long hospitalId, String hospitalName, long handled, long completed, long rejected,
            Double avgHoursToComplete
    ) {}

    public record Analytics(
            int days,
            long created, long completed, long rejected, long cancelled, long open,
            Double fulfilmentRate, Double avgHoursToComplete,
            List<DayPoint> series,
            Map<String, Long> byUrgency, Map<String, Long> byBloodGroup,
            List<HospitalPerformance> hospitals
    ) {}

    // ---------- broadcast ----------

    public record BroadcastRequest(
            @NotBlank @Pattern(regexp = "ALL|PATIENT|DONOR|HOSPITAL|BLOOD_BANK") String audience,
            @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "invalid blood group") String bloodGroup,
            @Size(max = 50) String city,
            Boolean onlyAvailable,
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 2000) String message,
            Boolean dryRun
    ) {}

    public record BroadcastResult(int recipients, boolean sent) {}
}
