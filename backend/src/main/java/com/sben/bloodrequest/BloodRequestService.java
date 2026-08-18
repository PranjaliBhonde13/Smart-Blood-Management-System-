package com.sben.bloodrequest;

import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.bloodrequest.dto.CreateBloodRequestRequest;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.common.util.BloodCompatibility;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Blood request lifecycle: creation by patients, verification/approval by
 * hospitals, and guarded status transitions. All state changes are validated
 * against {@link RequestStatus#ALLOWED_TRANSITIONS} so the workflow stays valid.
 */
@Service
@RequiredArgsConstructor
public class BloodRequestService {

    private final BloodRequestRepository repository;
    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final DonorRepository donorRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    /** Patient raises a request. Requires a completed patient profile. */
    @Transactional
    public BloodRequestResponse create(Long userId, CreateBloodRequestRequest req) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Complete your patient profile first"));
        BloodRequest request = BloodRequest.builder()
                .patientId(patient.getId())
                .bloodGroup(req.bloodGroup())
                .unitsRequired(req.unitsRequired())
                .urgency(req.urgency())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .status(RequestStatus.PENDING)
                .build();
        return BloodRequestResponse.from(repository.save(request));
    }

    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> getMine(Long userId, Pageable pageable) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Complete your patient profile first"));
        return repository.findByPatientIdAndDeletedFalse(patient.getId(), pageable)
                .map(BloodRequestResponse::from);
    }

    /** Approved requests a donor is blood-compatible to fulfil (donor feed). */
    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> openForDonor(Long donorUserId, Pageable pageable) {
        Donor donor = donorRepository.findByUserId(donorUserId)
                .orElseThrow(() -> new BusinessException("Complete your donor profile first"));
        List<String> recipientGroups = BloodCompatibility.recipientsFor(donor.getBloodGroup());
        return repository
                .findByStatusAndBloodGroupInAndDeletedFalse(RequestStatus.APPROVED, recipientGroups, pageable)
                .map(BloodRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> queue(RequestStatus status, Pageable pageable) {
        Page<BloodRequest> page = (status == null)
                ? repository.findByDeletedFalse(pageable)
                : repository.findByStatusAndDeletedFalse(status, pageable);
        return page.map(BloodRequestResponse::from);
    }

    /** Read one request, enforcing that only the owner, a hospital, or admin may view it. */
    @Transactional(readOnly = true)
    public BloodRequestResponse getById(Long id, Long userId, Role role) {
        BloodRequest request = findActive(id);
        if (role == Role.PATIENT) {
            Patient patient = patientRepository.findByUserId(userId).orElse(null);
            if (patient == null || !request.getPatientId().equals(patient.getId())) {
                throw new BusinessException("You cannot view this request", HttpStatus.FORBIDDEN);
            }
        }
        return BloodRequestResponse.from(request);
    }

    /** Hospital verifies a PENDING request and takes ownership of it. */
    @Transactional
    public BloodRequestResponse verify(Long id, Long hospitalUserId) {
        BloodRequest request = findActive(id);
        Hospital hospital = hospitalRepository.findByUserId(hospitalUserId)
                .orElseThrow(() -> new BusinessException("Complete your hospital profile first"));
        requireTransition(request, RequestStatus.VERIFIED);
        request.setHospitalId(hospital.getId());
        request.setStatus(RequestStatus.VERIFIED);
        BloodRequest saved = repository.save(request);
        notifyPatient(saved, "Request verified",
                "Your request #" + saved.getId() + " has been verified by a hospital.");
        return BloodRequestResponse.from(saved);
    }

    /** Generic guarded transition used by approve/reject/complete/etc. */
    @Transactional
    public BloodRequestResponse changeStatus(Long id, RequestStatus target) {
        BloodRequest request = findActive(id);
        requireTransition(request, target);
        request.setStatus(target);
        BloodRequest saved = repository.save(request);
        // Inform the patient of milestone status changes.
        if (target == RequestStatus.APPROVED || target == RequestStatus.REJECTED) {
            notifyPatient(saved, "Request " + target.name().toLowerCase(),
                    "Your request #" + saved.getId() + " is now " + target.name() + ".");
        }
        return BloodRequestResponse.from(saved);
    }

    /** Patient cancels their own request (if not already terminal). */
    @Transactional
    public BloodRequestResponse cancel(Long id, Long userId) {
        BloodRequest request = findActive(id);
        Patient patient = patientRepository.findByUserId(userId).orElse(null);
        if (patient == null || !request.getPatientId().equals(patient.getId())) {
            throw new BusinessException("You cannot cancel this request", HttpStatus.FORBIDDEN);
        }
        requireTransition(request, RequestStatus.CANCELLED);
        request.setStatus(RequestStatus.CANCELLED);
        return BloodRequestResponse.from(repository.save(request));
    }

    /** Patient attaches a medical report to their own request. */
    @Transactional
    public BloodRequestResponse uploadReport(Long id, Long userId, MultipartFile file) {
        BloodRequest request = findActive(id);
        Patient patient = patientRepository.findByUserId(userId).orElse(null);
        if (patient == null || !request.getPatientId().equals(patient.getId())) {
            throw new BusinessException("You cannot modify this request", HttpStatus.FORBIDDEN);
        }
        String stored = fileStorageService.store(file);
        request.setMedicalReport(stored);
        return BloodRequestResponse.from(repository.save(request));
    }

    // ---------- helpers ----------

    private BloodRequest findActive(Long id) {
        BloodRequest r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood request", id));
        if (r.isDeleted()) throw new ResourceNotFoundException("Blood request", id);
        return r;
    }

    private void notifyPatient(BloodRequest request, String title, String message) {
        patientRepository.findById(request.getPatientId())
                .map(Patient::getUserId)
                .ifPresent(uid -> notificationService.notify(uid, title, message, Notification.Type.REQUEST));
    }

    private void requireTransition(BloodRequest request, RequestStatus target) {
        if (!request.getStatus().canTransitionTo(target)) {
            throw new BusinessException(
                    "Cannot change status from %s to %s".formatted(request.getStatus(), target),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
