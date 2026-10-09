package com.sben.admin;

import com.sben.admin.dto.AdminViews.EscalationResult;
import com.sben.admin.dto.AdminViews.RequestRow;
import com.sben.audit.AuditService;
import com.sben.bloodrequest.BloodRequest;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.BloodRequestService;
import com.sben.bloodrequest.RequestSla;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodrequest.Urgency;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin oversight of every blood request: a filterable monitor with patient and
 * hospital names resolved, SLA ("stuck request") detection, and escalation.
 */
@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private final BloodRequestRepository requestRepository;
    private final BloodRequestService requestService;
    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalVerificationService verificationService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<RequestRow> search(RequestStatus status, Urgency urgency, String bloodGroup, Long hospitalId,
                                   LocalDate from, LocalDate to, Pageable pageable) {
        Page<BloodRequest> page = requestRepository.adminSearch(status, urgency,
                (bloodGroup == null || bloodGroup.isBlank()) ? null : bloodGroup.trim(),
                hospitalId,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                pageable);
        Map<Long, RequestRow> rows = toRows(page.getContent()).stream()
                .collect(Collectors.toMap(RequestRow::id, Function.identity()));
        return page.map(r -> rows.get(r.getId()));
    }

    /** Waiting requests past their SLA, most overdue first. */
    @Transactional(readOnly = true)
    public List<RequestRow> stuck() {
        return toRows(requestRepository.findByStatusInAndDeletedFalse(RequestSla.WAITING)).stream()
                .filter(RequestRow::overdue)
                .sorted(Comparator.comparingLong((RequestRow r) -> r.minutesInStatus() - r.slaMinutes()).reversed())
                .toList();
    }

    /**
     * Push a request forward: alert the owning hospital (or every verified
     * hospital when nobody owns it yet) and, if it is open for donors, re-alert
     * compatible donors nearby.
     */
    @Transactional
    public EscalationResult escalate(Long requestId) {
        BloodRequest request = requestRepository.findById(requestId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Blood request", requestId));
        if (!RequestSla.WAITING.contains(request.getStatus())) {
            throw new BusinessException("Only requests that are waiting (pending, verified or approved) can be escalated");
        }
        String title = "Escalated: " + request.getUrgency().name().toLowerCase() + " request #" + request.getId();
        String message = request.getUnitsRequired() + " unit(s) of " + request.getBloodGroup()
                + " — status " + request.getStatus().name().toLowerCase()
                + ". An administrator has flagged this request for immediate attention.";

        List<Hospital> hospitals = request.getHospitalId() != null
                ? hospitalRepository.findById(request.getHospitalId()).stream().toList()
                : hospitalRepository.findAll().stream().filter(h -> !h.isDeleted() && verificationService.isFullyVerified(h)).toList();
        hospitals.forEach(h -> notificationService.notify(h.getUserId(), title, message, Notification.Type.REQUEST));

        int donors = requestService.realertDonors(requestId);
        auditService.record("REQUEST_ESCALATED", "BloodRequest", requestId,
                "Alerted " + hospitals.size() + " hospital(s) and " + donors + " donor(s)");
        return new EscalationResult(requestId, donors, hospitals.size());
    }

    // ---------- helpers ----------

    private List<RequestRow> toRows(List<BloodRequest> requests) {
        Map<Long, Patient> patients = patientRepository.findAllById(
                        requests.stream().map(BloodRequest::getPatientId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, User> users = userRepository.findAllById(
                        patients.values().stream().map(Patient::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Hospital> hospitals = hospitalRepository.findAllById(
                        requests.stream().map(BloodRequest::getHospitalId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Hospital::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        return requests.stream().map(r -> {
            Patient p = patients.get(r.getPatientId());
            User pu = p == null ? null : users.get(p.getUserId());
            Hospital h = r.getHospitalId() == null ? null : hospitals.get(r.getHospitalId());
            LocalDateTime since = r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getRequestDate();
            long minutes = since == null ? 0 : Math.max(0, Duration.between(since, now).toMinutes());
            long sla = RequestSla.limitMinutes(r.getUrgency());
            return new RequestRow(r.getId(), r.getBloodGroup(), r.getUnitsRequired(), r.getUrgency(), r.getStatus(),
                    r.getRequestDate(), since,
                    pu == null ? null : pu.getId(), pu == null ? null : pu.getFullName(), pu == null ? null : pu.getPhone(),
                    r.getHospitalId(), h == null ? null : h.getHospitalName(),
                    r.getMedicalReport() != null, minutes, sla,
                    RequestSla.isOverdue(r.getStatus(), r.getUrgency(), minutes));
        }).toList();
    }
}
