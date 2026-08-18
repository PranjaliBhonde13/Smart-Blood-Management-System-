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
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.donation.DonationHistory;
import com.sben.donation.DonationHistoryRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.dto.HospitalResponse;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
                hospitalRepository.countByVerifiedFalseAndDeletedFalse(),
                bloodBankRepository.countByVerifiedFalseAndDeletedFalse(),
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
        Page<User> page = (role == null)
                ? userRepository.findByDeletedFalse(pageable)
                : userRepository.findByRoleAndDeletedFalse(role, pageable);
        return page.map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse setEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setEnabled(enabled);
        userRepository.save(user);
        auditService.record(enabled ? "USER_ENABLED" : "USER_DISABLED", "User", userId,
                "Account " + (enabled ? "enabled" : "disabled") + " for " + user.getEmail());
        return AdminUserResponse.from(user);
    }

    @Transactional
    public HospitalResponse verifyHospital(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));
        hospital.setVerified(true);
        hospitalRepository.save(hospital);
        auditService.record("HOSPITAL_VERIFIED", "Hospital", hospitalId, hospital.getHospitalName());
        notificationService.notify(hospital.getUserId(), "Hospital verified",
                "Your hospital has been verified and is now active on the network.",
                Notification.Type.VERIFICATION);
        return HospitalResponse.from(hospital);
    }

    @Transactional
    public BloodBankResponse verifyBloodBank(Long bankId) {
        BloodBank bank = bloodBankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank", bankId));
        bank.setVerified(true);
        bloodBankRepository.save(bank);
        auditService.record("BLOOD_BANK_VERIFIED", "BloodBank", bankId, bank.getBankName());
        notificationService.notify(bank.getUserId(), "Blood bank verified",
                "Your blood bank has been verified and will now appear in patient searches.",
                Notification.Type.VERIFICATION);
        return BloodBankResponse.from(bank);
    }

    @Transactional(readOnly = true)
    public PendingVerifications pendingVerifications() {
        return new PendingVerifications(
                hospitalRepository.findByVerifiedFalseAndDeletedFalse()
                        .stream().map(HospitalResponse::from).toList(),
                bloodBankRepository.findByVerifiedFalseAndDeletedFalse()
                        .stream().map(BloodBankResponse::from).toList()
        );
    }
}
