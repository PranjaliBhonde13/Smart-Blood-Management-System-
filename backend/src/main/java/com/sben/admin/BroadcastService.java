package com.sben.admin;

import com.sben.admin.dto.AdminViews.BroadcastRequest;
import com.sben.admin.dto.AdminViews.BroadcastResult;
import com.sben.audit.AuditService;
import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.donor.Availability;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Targeted announcements from admins (e.g. "O- donors in Mumbai: urgent need").
 * Each recipient gets an in-app notification plus the matching email. A dry
 * run returns the audience size without sending anything.
 */
@Service
@RequiredArgsConstructor
public class BroadcastService {

    /** Safety cap per broadcast. */
    static final int MAX_RECIPIENTS = 2000;

    private final UserRepository userRepository;
    private final DonorRepository donorRepository;
    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bankRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public BroadcastResult broadcast(BroadcastRequest req) {
        List<User> recipients = resolve(req);
        if (Boolean.TRUE.equals(req.dryRun())) {
            return new BroadcastResult(recipients.size(), false);
        }
        recipients.forEach(u -> notificationService.notify(u.getId(), req.title().trim(), req.message().trim(),
                Notification.Type.SYSTEM));
        auditService.record("BROADCAST_SENT", "Broadcast", null,
                "\"" + req.title().trim() + "\" to " + describe(req) + " — " + recipients.size() + " recipient(s)");
        return new BroadcastResult(recipients.size(), true);
    }

    private List<User> resolve(BroadcastRequest req) {
        String city = blankToNull(req.city());
        String group = blankToNull(req.bloodGroup());
        boolean onlyAvailable = Boolean.TRUE.equals(req.onlyAvailable());
        Role role = "ALL".equals(req.audience()) ? null : Role.valueOf(req.audience());

        // Active, verified, non-admin accounts of the requested role.
        Map<Long, User> users = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && u.isEmailVerified() && !u.isDeleted() && u.getRole() != Role.ADMIN)
                .filter(u -> role == null || u.getRole() == role)
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> keep;
        if (role == Role.DONOR) {
            keep = donorRepository.findAll().stream()
                    .filter(d -> !d.isDeleted())
                    .filter(d -> group == null || group.equals(d.getBloodGroup()))
                    .filter(d -> city == null || city.equalsIgnoreCase(trim(d.getCity())))
                    .filter(d -> !onlyAvailable || d.getAvailability() == Availability.AVAILABLE)
                    .map(Donor::getUserId).collect(Collectors.toSet());
        } else if (role == Role.PATIENT) {
            keep = patientRepository.findAll().stream()
                    .filter(p -> group == null || group.equals(p.getBloodGroup()))
                    .filter(p -> city == null || (p.getAddress() != null && p.getAddress().toLowerCase().contains(city.toLowerCase())))
                    .map(Patient::getUserId).collect(Collectors.toSet());
            if (group == null && city == null) keep = users.keySet();
        } else if (role == Role.HOSPITAL) {
            keep = hospitalRepository.findAll().stream()
                    .filter(h -> city == null || city.equalsIgnoreCase(trim(h.getCity())))
                    .map(Hospital::getUserId).collect(Collectors.toSet());
            if (city == null) keep = users.keySet();
        } else if (role == Role.BLOOD_BANK) {
            keep = bankRepository.findAll().stream()
                    .filter(b -> city == null || city.equalsIgnoreCase(trim(b.getCity())))
                    .map(BloodBank::getUserId).collect(Collectors.toSet());
            if (city == null) keep = users.keySet();
        } else {
            keep = users.keySet();
        }
        Set<Long> finalKeep = keep;
        return users.values().stream()
                .filter(u -> finalKeep.contains(u.getId()))
                .limit(MAX_RECIPIENTS)
                .toList();
    }

    private static String describe(BroadcastRequest req) {
        StringBuilder sb = new StringBuilder("ALL".equals(req.audience()) ? "all users" : req.audience());
        if (blankToNull(req.bloodGroup()) != null) sb.append(" ").append(req.bloodGroup());
        if (blankToNull(req.city()) != null) sb.append(" in ").append(req.city().trim());
        if (Boolean.TRUE.equals(req.onlyAvailable())) sb.append(" (available only)");
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
