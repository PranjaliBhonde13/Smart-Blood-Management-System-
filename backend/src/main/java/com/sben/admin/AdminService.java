package com.sben.admin;

import com.sben.admin.dto.AdminUserResponse;
import com.sben.admin.dto.DashboardStats;
import com.sben.admin.dto.PendingVerifications;
import com.sben.audit.AuditService;
import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.donation.DonationHistory;
import com.sben.donation.DonationHistoryRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalDocumentRepository;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.hospital.document.dto.HospitalDocumentViews.Compliance;
import com.sben.hospital.dto.HospitalResponse;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import com.sben.notification.email.AccountEmails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrative operations: platform KPIs, user management, and verification of
 * hospitals/blood banks. Every state-changing action is audited and, where
 * relevant, the affected user is notified.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodRequestRepository requestRepository;
    private final DonationHistoryRepository donationRepository;
    private final BloodStockRepository stockRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final HospitalVerificationService verificationService;
    private final HospitalDocumentRepository documentRepository;
    private final AccountEmails accountEmails;

    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (RequestStatus s : RequestStatus.values()) {
            byStatus.put(s.name(), requestRepository.countByStatusAndDeletedFalse(s));
        }
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return new DashboardStats(
                userRepository.countByDeletedFalse(),
                userRepository.countByRoleAndDeletedFalse(Role.PATIENT),
                userRepository.countByRoleAndDeletedFalse(Role.DONOR),
                userRepository.countByRoleAndDeletedFalse(Role.HOSPITAL),
                userRepository.countByRoleAndDeletedFalse(Role.BLOOD_BANK),
                hospitalsAwaitingReview().size(),
                bloodBankRepository.countByVerifiedFalseAndRejectedAtIsNullAndLicenseDocumentIsNotNullAndDeletedFalse(),
                requestRepository.countByDeletedFalse(),
                requestRepository.countByStatusAndDeletedFalse(RequestStatus.PENDING),
                requestRepository.countByStatusAndDeletedFalse(RequestStatus.COMPLETED),
                requestRepository.countByRequestDateAfterAndDeletedFalse(startOfToday),
                donationRepository.countByStatus(DonationHistory.Status.COMPLETED),
                stockRepository.totalUnits(),
                byStatus
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Role role, Pageable pageable) {
        return searchUsers(role, null, null, pageable);
    }

    /** Users filtered by role, status (enabled) and free text on name/email/phone. */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchUsers(Role role, Boolean enabled, String q, Pageable pageable) {
        String pattern = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return userRepository.search(role, enabled, pattern, pageable).map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse setEnabled(Long userId, boolean enabled, Long actorUserId) {
        return setEnabled(userId, enabled, actorUserId, null);
    }

    /** Enable or suspend an account. A suspension reason is shown to the user at sign-in. */
    @Transactional
    public AdminUserResponse setEnabled(Long userId, boolean enabled, Long actorUserId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean changed = user.isEnabled() != enabled;
        if (!enabled) {
            if (userId.equals(actorUserId)) {
                throw new BusinessException("You cannot disable your own account");
            }
            if (user.getRole() == Role.ADMIN && user.isEnabled()
                    && userRepository.countByRoleAndEnabledTrueAndDeletedFalse(Role.ADMIN) <= 1) {
                throw new BusinessException("Cannot disable the last active administrator");
            }
            // Suspension takes effect immediately: existing access tokens stop working.
            user.revokeIssuedTokens();
            user.setSuspensionReason(reason == null || reason.isBlank() ? null : reason.trim());
        } else {
            user.setSuspensionReason(null);
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        if (changed) { // the user can no longer sign in to see an in-app notice, so email is how they learn
            if (enabled) accountEmails.accountRestored(user);
            else accountEmails.accountSuspended(user, user.getSuspensionReason());
        }
        auditService.record(enabled ? "USER_ENABLED" : "USER_DISABLED", "User", userId,
                "Account " + (enabled ? "enabled" : "disabled") + " for " + user.getEmail()
                        + (!enabled && user.getSuspensionReason() != null ? " — reason: " + user.getSuspensionReason() : ""));
        return AdminUserResponse.from(user);
    }

    @Transactional
    public HospitalResponse verifyHospital(Long hospitalId) {
        return verifyHospital(hospitalId, null);
    }

    /**
     * Approve a hospital. Refused until every applicable mandatory document is
     * approved and unexpired — like blood banks, completeness alone never verifies.
     */
    @Transactional
    public HospitalResponse verifyHospital(Long hospitalId, Long actorUserId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));
        Compliance compliance = verificationService.assess(hospital);
        if (!compliance.documentsComplete()) {
            throw new BusinessException("Cannot verify: these mandatory documents are not approved and valid: "
                    + String.join(", ", compliance.outstanding()), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        hospital.setVerified(true);
        hospital.setVerifiedAt(LocalDateTime.now());
        hospital.setVerifiedBy(actorUserId);
        hospital.setRejectionReason(null);
        hospital.setRejectedAt(null);
        hospitalRepository.save(hospital);
        auditService.record("HOSPITAL_VERIFIED", "Hospital", hospitalId, hospital.getHospitalName());
        notificationService.notify(hospital.getUserId(), "Hospital verified",
                "Your hospital has been verified and is now active on the network.",
                Notification.Type.VERIFICATION);
        return HospitalResponse.from(hospital, verificationService.assess(hospital));
    }

    @Transactional
    public BloodBankResponse verifyBloodBank(Long bankId) {
        return verifyBloodBank(bankId, null);
    }

    /**
     * Approve a blood bank's license. Refused when there is no certificate to
     * check or the license has already expired — completeness alone never verifies.
     */
    @Transactional
    public BloodBankResponse verifyBloodBank(Long bankId, Long actorUserId) {
        BloodBank bank = bloodBankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank", bankId));
        if (bank.getLicenseDocument() == null) {
            throw new BusinessException("Cannot verify: no license certificate has been uploaded",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (bank.isLicenseExpired()) {
            throw new BusinessException("Cannot verify: the license expired on " + bank.getLicenseExpiryDate(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        bank.setVerified(true);
        bank.setVerifiedAt(LocalDateTime.now());
        bank.setVerifiedBy(actorUserId);
        bank.setRejectionReason(null);
        bank.setRejectedAt(null);
        bloodBankRepository.save(bank);
        auditService.record("BLOOD_BANK_VERIFIED", "BloodBank", bankId, bank.getBankName());
        notificationService.notify(bank.getUserId(), "Blood bank verified",
                "Your blood bank has been verified and will now appear in patient searches.",
                Notification.Type.VERIFICATION);
        return BloodBankResponse.from(bank);
    }

    /** Reject a hospital's verification; it leaves the queue until the owner edits and resubmits. */
    @Transactional
    public HospitalResponse rejectHospital(Long hospitalId, String reason) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));
        hospital.setVerified(false);
        hospital.setVerifiedAt(null);
        hospital.setVerifiedBy(null);
        hospital.setRejectionReason(requireReason(reason));
        hospital.setRejectedAt(LocalDateTime.now());
        hospitalRepository.save(hospital);
        auditService.record("HOSPITAL_REJECTED", "Hospital", hospitalId,
                hospital.getHospitalName() + " — " + hospital.getRejectionReason());
        notificationService.notify(hospital.getUserId(), "Hospital verification rejected",
                "Your hospital verification was not approved: " + hospital.getRejectionReason()
                        + ". Update your profile and save it to resubmit.",
                Notification.Type.VERIFICATION);
        return HospitalResponse.from(hospital);
    }

    /** Reject a blood bank's verification; it leaves the queue until the owner edits and resubmits. */
    @Transactional
    public BloodBankResponse rejectBloodBank(Long bankId, String reason) {
        BloodBank bank = bloodBankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank", bankId));
        bank.setVerified(false);
        bank.setVerifiedAt(null);
        bank.setVerifiedBy(null);
        bank.setRejectionReason(requireReason(reason));
        bank.setRejectedAt(LocalDateTime.now());
        bloodBankRepository.save(bank);
        auditService.record("BLOOD_BANK_REJECTED", "BloodBank", bankId,
                bank.getBankName() + " — " + bank.getRejectionReason());
        notificationService.notify(bank.getUserId(), "Blood bank verification rejected",
                "Your blood bank verification was not approved: " + bank.getRejectionReason()
                        + ". Update your profile and save it to resubmit.",
                Notification.Type.VERIFICATION);
        return BloodBankResponse.from(bank);
    }

    @Transactional(readOnly = true)
    public PendingVerifications pendingVerifications() {
        return new PendingVerifications(
                hospitalsAwaitingReview().stream()
                        .map(h -> HospitalResponse.from(h, verificationService.assess(h))).toList(),
                // Only banks that have actually submitted a certificate are reviewable.
                bloodBankRepository.findByVerifiedFalseAndRejectedAtIsNullAndLicenseDocumentIsNotNullAndDeletedFalse()
                        .stream().map(BloodBankResponse::from).toList()
        );
    }

    /**
     * Hospitals an admin needs to look at: awaiting a hospital-level decision, or
     * with at least one current document awaiting review (e.g. a replacement
     * uploaded by an already verified hospital).
     */
    private List<Hospital> hospitalsAwaitingReview() {
        Map<Long, Hospital> byId = new LinkedHashMap<>();
        hospitalRepository.findByVerifiedFalseAndRejectedAtIsNullAndDeletedFalse().forEach(h -> byId.put(h.getId(), h));
        List<Long> withPendingDocs = documentRepository.findHospitalIdsWithPendingReview(LocalDate.now()).stream()
                .filter(id -> !byId.containsKey(id)).toList();
        hospitalRepository.findAllById(withPendingDocs).stream()
                .filter(h -> !h.isDeleted()).forEach(h -> byId.put(h.getId(), h));
        return List.copyOf(byId.values());
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A reason is required to reject a verification", HttpStatus.BAD_REQUEST);
        }
        String r = reason.trim();
        return r.length() > 500 ? r.substring(0, 500) : r;
    }
}
