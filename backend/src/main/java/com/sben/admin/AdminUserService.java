package com.sben.admin;

import com.sben.admin.dto.AdminUserResponse;
import com.sben.admin.dto.AdminViews.CreateAdminRequest;
import com.sben.admin.dto.AdminViews.UserDetail;
import com.sben.audit.AuditLogRepository;
import com.sben.audit.AuditService;
import com.sben.audit.dto.AuditLogResponse;
import com.sben.auth.AuthService;
import com.sben.auth.dto.EmailRequest;
import com.sben.auth.token.RefreshTokenService;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.donation.DonationHistory;
import com.sben.donation.DonationHistoryRepository;
import com.sben.donation.dto.DonationResponse;
import com.sben.donor.DonorRepository;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin user management beyond enable/disable: a 360° user view, creating
 * further administrators, forcing a sign-out and sending a password reset.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalVerificationService verificationService;
    private final BloodBankRepository bloodBankRepository;
    private final BloodRequestRepository requestRepository;
    private final DonationHistoryRepository donationRepository;
    private final BloodStockRepository stockRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    /** Profile, activity and history for one user. */
    @Transactional(readOnly = true)
    public UserDetail detail(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Long> stats = new LinkedHashMap<>();
        List<BloodRequestResponse> requests = List.of();
        List<DonationResponse> donations = List.of();

        switch (user.getRole()) {
            case PATIENT -> patientRepository.findByUserId(userId).ifPresent(p -> {
                profile.put("Blood group", p.getBloodGroup());
                profile.put("Age", p.getAge());
                profile.put("Gender", p.getGender());
                profile.put("Address", p.getAddress());
                profile.put("Emergency contact", p.getEmergencyContact());
                stats.put("Requests raised", requestRepository.countByPatientIdAndDeletedFalse(p.getId()));
            });
            case DONOR -> donorRepository.findByUserId(userId).ifPresent(d -> {
                profile.put("Blood group", d.getBloodGroup());
                profile.put("Age", d.currentAge());
                profile.put("City", d.getCity());
                profile.put("State", d.getState());
                profile.put("Availability", d.getAvailability());
                profile.put("Receives donation requests", d.getContactConsent() == null ? "Not answered"
                        : d.getContactConsent() ? "Yes" : "No (opted out)");
                profile.put("Last donation", d.getLastDonationDate());
                stats.put("Completed donations", donationRepository.countByDonorIdAndStatus(d.getId(), DonationHistory.Status.COMPLETED));
                stats.put("Scheduled donations", donationRepository.countByDonorIdAndStatus(d.getId(), DonationHistory.Status.SCHEDULED));
            });
            case HOSPITAL -> hospitalRepository.findByUserId(userId).ifPresent(h -> {
                profile.put("Hospital", h.getHospitalName());
                profile.put("License", h.getLicenseNumber());
                profile.put("Phone", h.getPhone());
                profile.put("24x7 emergency phone", h.getEmergencyPhone());
                profile.put("Official email", h.getOfficialEmail());
                profile.put("Contact person", h.getContactPersonName() == null ? null
                        : h.getContactPersonName() + (h.getContactPersonDesignation() == null ? "" : " (" + h.getContactPersonDesignation() + ")"));
                profile.put("Address", h.getAddress());
                profile.put("City", h.getCity());
                profile.put("State", h.getState());
                profile.put("Verification", verificationService.assess(h).message());
                stats.put("Requests handled", requestRepository.countByHospitalIdAndDeletedFalse(h.getId()));
            });
            case BLOOD_BANK -> bloodBankRepository.findByUserId(userId).ifPresent(b -> {
                profile.put("Blood bank", b.getBankName());
                profile.put("License", b.getLicenseNumber());
                profile.put("Phone", b.getPhone());
                profile.put("Address", b.getAddress());
                profile.put("City", b.getCity());
                profile.put("Verification", switch (b.getVerificationStatus()) {
                    case VERIFIED -> "Verified";
                    case EXPIRED -> "License expired " + b.getLicenseExpiryDate();
                    case REJECTED -> "Rejected: " + b.getRejectionReason();
                    case NOT_SUBMITTED -> "License not submitted";
                    case PENDING -> "Pending";
                });
                stats.put("Units in stock", stockRepository.totalUnitsForBank(b.getId()));
                stats.put("Requests fulfilled", donationRepository.countByBloodBankIdAndStatus(b.getId(), DonationHistory.Status.COMPLETED));
            });
            case ADMIN -> profile.put("Role", "Administrator");
        }

        if (user.getRole() == Role.PATIENT) {
            requests = patientRepository.findByUserId(userId)
                    .map(p -> requestRepository.findTop20ByPatientIdAndDeletedFalseOrderByRequestDateDesc(p.getId())
                            .stream().map(BloodRequestResponse::from).toList())
                    .orElse(List.of());
        }
        if (user.getRole() == Role.DONOR) {
            donations = donorRepository.findByUserId(userId)
                    .map(d -> donationRepository.findTop20ByDonorIdOrderByCreatedAtDesc(d.getId())
                            .stream().map(DonationResponse::from).toList())
                    .orElse(List.of());
        }
        List<AuditLogResponse> activity = auditLogRepository.forUser(userId, PageRequest.of(0, 20))
                .stream().map(AuditLogResponse::from).toList();

        return new UserDetail(AdminUserResponse.from(user), profile, stats, requests, donations, activity);
    }

    /** Create another administrator (public sign-up cannot create admins). */
    @Transactional
    public AdminUserResponse createAdmin(CreateAdminRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered", HttpStatus.CONFLICT);
        }
        User admin = userRepository.save(User.builder()
                .fullName(req.fullName())
                .email(req.email())
                .phone(req.phone())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.ADMIN)
                .emailVerified(true)
                .enabled(true)
                .build());
        auditService.record("ADMIN_CREATED", "User", admin.getId(), "New administrator " + admin.getEmail());
        return AdminUserResponse.from(admin);
    }

    /** Sign the user out everywhere: refresh tokens revoked, access tokens rejected. */
    @Transactional
    public void forceLogout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        refreshTokenService.revokeAll(userId);
        user.revokeIssuedTokens();
        userRepository.save(user);
        auditService.record("USER_FORCE_LOGOUT", "User", userId, "Signed out everywhere: " + user.getEmail());
    }

    /** Email the user a password-reset code (same flow as "Forgot password"). */
    @Transactional
    public void sendPasswordReset(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        authService.forgotPassword(new EmailRequest(user.getEmail()));
        auditService.record("USER_PASSWORD_RESET_SENT", "User", userId, "Reset code sent to " + user.getEmail());
    }
}
