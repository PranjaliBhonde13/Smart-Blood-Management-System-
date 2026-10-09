package com.sben.bloodrequest;

import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.bloodrequest.dto.CreateBloodRequestRequest;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.common.util.BloodCompatibility;
import com.sben.common.util.DonorEligibility;
import com.sben.common.util.GeoUtils;
import com.sben.donation.DonationHistory;
import com.sben.donation.DonationHistoryRepository;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.patient.PatientService;
import com.sben.audit.AuditService;
import com.sben.bloodrequest.dto.ConfirmBloodGroupRequest;
import com.sben.common.exception.FieldValidationException;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blood request lifecycle: creation by patients, verification/approval by
 * hospitals, and guarded status transitions. All state changes are validated
 * against {@link RequestStatus#ALLOWED_TRANSITIONS} so the workflow stays valid.
 *
 * <p>Actor rules: only a fully verified hospital (admin-verified, with every
 * mandatory document approved and unexpired) may verify a request, and once a
 * hospital owns a request only that hospital (or an admin) may move it on. A
 * hospital that loses verification keeps ownership of its requests but cannot
 * act on them until it is verified again; admins can still move them on.
 */
@Service
@RequiredArgsConstructor
public class BloodRequestService {

    /** Radius used to alert donors when a request with a location is approved. */
    private static final double DONOR_ALERT_RADIUS_KM = 25.0;
    private static final int MAX_DONOR_ALERTS = 50;

    private final BloodRequestRepository repository;
    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final DonorRepository donorRepository;
    private final DonationHistoryRepository donationRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final HospitalVerificationService verificationService;
    private final PatientService patientService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** How far ahead a required-by time may be set. */
    private static final int MAX_REQUIRED_BY_DAYS = 90;

    /**
     * Patient raises a request, for themselves or for another patient.
     *
     * <p>SELF: the group is read from the requester's saved profile on the server;
     * a client-sent group must match it. Whether it counts as verified comes from
     * the blood-group document, never from the client. OTHER: the requester's own
     * group is never used; the declared group needs hospital/admin confirmation
     * before the request can be approved. Submission itself is never blocked by
     * verification, so emergencies can always be raised.
     */
    @Transactional
    public BloodRequestResponse create(Long userId, CreateBloodRequestRequest req) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Complete your patient profile first"));
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        if (req.requiredBy().isBefore(now.minusMinutes(5))) {
            errors.put("requiredBy", "Required-by time must be in the future");
        } else if (req.requiredBy().isAfter(now.plusDays(MAX_REQUIRED_BY_DAYS))) {
            errors.put("requiredBy", "Required-by time must be within " + MAX_REQUIRED_BY_DAYS + " days");
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            errors.put(req.latitude() == null ? "latitude" : "longitude", "Enter both latitude and longitude, or neither");
        }

        String bloodGroup;
        BloodGroupSource source;
        String recipientName;
        Integer recipientAge;
        String relationship = null;
        if (req.requestFor() == RequestFor.SELF) {
            PatientService.ProfileBloodGroup own = patientService.profileBloodGroup(patient);
            if (own.bloodGroup() == null) {
                throw new BusinessException("Add your blood group to your profile before requesting blood for yourself",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (req.bloodGroup() != null && !req.bloodGroup().isBlank() && !req.bloodGroup().equals(own.bloodGroup())) {
                errors.put("bloodGroup", "For yourself, the blood group comes from your profile (" + own.bloodGroup()
                        + "). To request a different group, choose Another patient.");
            }
            bloodGroup = own.bloodGroup();
            source = own.verified() ? BloodGroupSource.PROFILE_VERIFIED : BloodGroupSource.PROFILE_UNVERIFIED;
            recipientName = userRepository.findById(userId).map(User::getFullName).orElse(null);
            recipientAge = patient.currentAge();
        } else {
            if (req.bloodGroup() == null || req.bloodGroup().isBlank()) errors.put("bloodGroup", "Choose the patient's blood group");
            if (req.recipientName() == null || req.recipientName().isBlank()) errors.put("recipientName", "Enter the patient's name");
            if (req.recipientAge() == null) errors.put("recipientAge", "Enter the patient's age");
            bloodGroup = req.bloodGroup();
            source = BloodGroupSource.DECLARED;
            recipientName = req.recipientName() == null ? null : req.recipientName().trim().replaceAll("\\s+", " ");
            recipientAge = req.recipientAge();
            relationship = blankToNull(req.recipientRelationship());
        }

        Hospital preferred = null;
        if (req.preferredHospitalId() != null) {
            preferred = hospitalRepository.findById(req.preferredHospitalId())
                    .filter(h -> !h.isDeleted() && verificationService.isFullyVerified(h))
                    .orElse(null);
            if (preferred == null) errors.put("preferredHospitalId", "Choose a verified hospital from the list");
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        BloodRequest request = BloodRequest.builder()
                .patientId(patient.getId())
                .requestFor(req.requestFor())
                .recipientName(recipientName)
                .recipientAge(recipientAge)
                .recipientRelationship(relationship)
                .bloodGroup(bloodGroup)
                .bloodGroupSource(source)
                .component(req.component())
                .unitsRequired(req.unitsRequired())
                .urgency(req.urgency())
                .requiredBy(req.requiredBy().withSecond(0).withNano(0))
                .preferredHospitalId(preferred == null ? null : preferred.getId())
                .preferredHospitalName(preferred == null ? null : preferred.getHospitalName())
                .locationNotes(blankToNull(req.locationNotes()))
                .latitude(req.latitude())
                .longitude(req.longitude())
                .status(RequestStatus.PENDING)
                .build();
        BloodRequest saved = repository.save(request);
        if (preferred != null) {
            notificationService.notify(preferred.getUserId(), "New blood request names your hospital",
                    saved.getUnitsRequired() + " unit(s) of " + saved.getBloodGroup() + " (" + saved.getUrgency().name().toLowerCase()
                            + " urgency) — request #" + saved.getId() + " is in your request queue.",
                    Notification.Type.REQUEST);
        }
        return BloodRequestResponse.from(saved);
    }

    /** Verified hospitals a patient can name as their preferred hospital. */
    @Transactional(readOnly = true)
    public List<HospitalOption> hospitalOptions() {
        return hospitalRepository.findAll().stream()
                .filter(h -> !h.isDeleted() && verificationService.isFullyVerified(h))
                .sorted(Comparator.comparing(Hospital::getHospitalName, String.CASE_INSENSITIVE_ORDER))
                .map(h -> new HospitalOption(h.getId(), h.getHospitalName(), h.getCity(), h.getState()))
                .toList();
    }

    public record HospitalOption(Long id, String name, String city, String state) {}

    /**
     * A hospital (owning the request, or it is unowned) or an admin confirms the
     * requested blood group, or corrects it with a reason. Allowed only before
     * approval, so donors are never alerted for a group that later changes.
     * This records who validated the request; it is not a compatibility decision.
     */
    @Transactional
    public BloodRequestResponse confirmBloodGroup(Long id, ConfirmBloodGroupRequest req, Long actorUserId, Role role) {
        BloodRequest request = findActive(id);
        if (role == Role.HOSPITAL) {
            Hospital hospital = requireVerifiedHospital(actorUserId);
            requireHospitalOwnsOrUnowned(request, hospital);
        } else if (role != Role.ADMIN) {
            throw new BusinessException("You do not have permission to perform this action", HttpStatus.FORBIDDEN);
        }
        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.VERIFIED) {
            throw new BusinessException("The blood group can only be confirmed or corrected before approval",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String previous = request.getBloodGroup();
        boolean correction = !previous.equals(req.bloodGroup());
        String reason = blankToNull(req.reason());
        if (correction && reason == null) {
            throw new FieldValidationException(Map.of("reason", "Give a reason for correcting the blood group"));
        }
        request.setBloodGroup(req.bloodGroup());
        request.setBloodGroupConfirmedBy(actorUserId);
        request.setBloodGroupConfirmedAt(LocalDateTime.now().withNano(0));
        BloodRequest saved = repository.save(request);

        auditService.record(correction ? "BLOOD_REQUEST_GROUP_CORRECTED" : "BLOOD_REQUEST_GROUP_CONFIRMED",
                "BloodRequest", id, (correction ? previous + " → " + req.bloodGroup() : req.bloodGroup())
                        + " by " + role + (reason != null ? " — " + reason : ""));
        if (correction) {
            notifyPatient(saved, "Blood group corrected on request #" + id,
                    "The requested blood group was changed from " + previous + " to " + req.bloodGroup()
                            + " by " + (role == Role.ADMIN ? "an administrator" : "the hospital") + ". Reason: " + reason);
        }
        return BloodRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> getMine(Long userId, Pageable pageable) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Complete your patient profile first"));
        return repository.findByPatientIdAndDeletedFalse(patient.getId(), pageable)
                .map(BloodRequestResponse::from);
    }

    /** Approved requests a donor is blood-compatible to fulfil and hasn't declined (donor feed). */
    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> openForDonor(Long donorUserId, Pageable pageable) {
        Donor donor = donorRepository.findByUserId(donorUserId)
                .orElseThrow(() -> new BusinessException("Complete your donor profile first"));
        List<String> recipientGroups = BloodCompatibility.recipientsFor(donor.getBloodGroup());
        return repository
                .findOpenForDonor(RequestStatus.APPROVED, recipientGroups, donor.getId(), pageable)
                .map(BloodRequestResponse::redacted); // donors never see the recipient's identity
    }

    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> queue(RequestStatus status, Pageable pageable) {
        return queue(status, pageable, Role.ADMIN);
    }

    /** Request queue; blood banks get the redacted view (no recipient identity). */
    @Transactional(readOnly = true)
    public Page<BloodRequestResponse> queue(RequestStatus status, Pageable pageable, Role role) {
        Page<BloodRequest> page = (status == null)
                ? repository.findByDeletedFalse(pageable)
                : repository.findByStatusAndDeletedFalse(status, pageable);
        return page.map(role == Role.BLOOD_BANK ? BloodRequestResponse::redacted : BloodRequestResponse::from);
    }

    /**
     * Read one request. Patients see only their own; donors see approved
     * requests they are compatible with (or ones they donated to); hospitals
     * and admins see all.
     */
    @Transactional(readOnly = true)
    public BloodRequestResponse getById(Long id, Long userId, Role role) {
        BloodRequest request = findActive(id);
        if (role == Role.PATIENT) {
            requireOwner(request, userId, "You cannot view this request");
        } else if (role == Role.DONOR) {
            Donor donor = donorRepository.findByUserId(userId).orElse(null);
            boolean visible = donor != null && (
                    (request.getStatus() == RequestStatus.APPROVED
                            && BloodCompatibility.donorsFor(request.getBloodGroup()).contains(donor.getBloodGroup()))
                    || donationRepository.findByRequestIdOrderByCreatedAtDesc(id).stream()
                            .anyMatch(d -> donor.getId().equals(d.getDonorId())));
            if (!visible) throw new BusinessException("You cannot view this request", HttpStatus.FORBIDDEN);
            return BloodRequestResponse.redacted(request);
        }
        return BloodRequestResponse.from(request);
    }

    /** Verified hospital verifies a PENDING request and takes ownership of it. */
    @Transactional
    public BloodRequestResponse verify(Long id, Long hospitalUserId) {
        BloodRequest request = findActive(id);
        Hospital hospital = requireVerifiedHospital(hospitalUserId);
        requireTransition(request, RequestStatus.VERIFIED);
        request.setHospitalId(hospital.getId());
        request.setStatus(RequestStatus.VERIFIED);
        BloodRequest saved = repository.save(request);
        notifyPatient(saved, "Request verified",
                "Your request #" + saved.getId() + " has been verified by a hospital.");
        return BloodRequestResponse.from(saved);
    }

    /**
     * Status change requested through the API by a hospital or admin. Verifying
     * has its own endpoint (it assigns the hospital), and a hospital may only act
     * on requests it owns (or unowned PENDING ones, which it then owns).
     */
    @Transactional
    public BloodRequestResponse updateStatus(Long id, RequestStatus target, Long actorUserId, Role role) {
        BloodRequest request = findActive(id);
        if (target == RequestStatus.VERIFIED) {
            throw new BusinessException("Use the verify action to verify a request");
        }
        if (role == Role.HOSPITAL) {
            Hospital hospital = requireVerifiedHospital(actorUserId);
            requireHospitalOwnsOrUnowned(request, hospital);
            if (request.getHospitalId() == null) request.setHospitalId(hospital.getId());
        } else if (role != Role.ADMIN) {
            throw new BusinessException("You do not have permission to perform this action", HttpStatus.FORBIDDEN);
        }
        return changeStatus(request, target);
    }

    /**
     * Guarded transition used internally (donation flows) after the caller has
     * done its own authorisation.
     */
    @Transactional
    public BloodRequestResponse changeStatus(Long id, RequestStatus target) {
        return changeStatus(findActive(id), target);
    }

    /** Patient cancels their own request (if not already terminal). */
    @Transactional
    public BloodRequestResponse cancel(Long id, Long userId) {
        BloodRequest request = findActive(id);
        requireOwner(request, userId, "You cannot cancel this request");
        return changeStatus(request, RequestStatus.CANCELLED);
    }

    /** Patient attaches a medical report to their own, still-open request. */
    @Transactional
    public BloodRequestResponse uploadReport(Long id, Long userId, MultipartFile file) {
        BloodRequest request = findActive(id);
        requireOwner(request, userId, "You cannot modify this request");
        if (RequestStatus.TERMINAL.contains(request.getStatus())) {
            throw new BusinessException("Reports can only be attached to open requests");
        }
        String stored = fileStorageService.store(file);
        request.setMedicalReport(stored);
        return BloodRequestResponse.from(repository.save(request));
    }

    /** Path of a request's medical report, for the owner patient, hospitals and admins. */
    @Transactional(readOnly = true)
    public Path loadReport(Long id, Long userId, Role role) {
        BloodRequest request = findActive(id);
        if (role == Role.PATIENT) {
            requireOwner(request, userId, "You cannot view this report");
        } else if (role != Role.HOSPITAL && role != Role.ADMIN) {
            throw new BusinessException("You cannot view this report", HttpStatus.FORBIDDEN);
        }
        if (request.getMedicalReport() == null) {
            throw new ResourceNotFoundException("No medical report attached to request " + id);
        }
        return fileStorageService.load(request.getMedicalReport());
    }

    /**
     * Admin escalation: re-alert compatible donors for an open (APPROVED) request.
     * Returns how many donors were notified.
     */
    @Transactional
    public int realertDonors(Long id) {
        BloodRequest request = findActive(id);
        if (request.getStatus() != RequestStatus.APPROVED) return 0;
        return alertCompatibleDonors(request);
    }

    // ---------- helpers ----------

    private BloodRequestResponse changeStatus(BloodRequest request, RequestStatus target) {
        requireTransition(request, target);
        if (target == RequestStatus.APPROVED && !request.isBloodGroupApprovable()) {
            throw new BusinessException("Confirm the patient's blood group before approving: it was "
                    + (request.getBloodGroupSource() == BloodGroupSource.DECLARED
                        ? "entered for another patient" : "taken from an unverified profile")
                    + " and needs hospital or administrator confirmation.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        request.setStatus(target);
        BloodRequest saved = repository.save(request);
        // Inform the patient of milestone status changes.
        if (target == RequestStatus.APPROVED || target == RequestStatus.REJECTED) {
            notifyPatient(saved, "Request " + target.name().toLowerCase(),
                    "Your request #" + saved.getId() + " is now " + target.name() + ".");
        }
        if (target == RequestStatus.APPROVED) {
            alertCompatibleDonors(saved);
        }
        if (target == RequestStatus.CANCELLED || target == RequestStatus.REJECTED) {
            cancelScheduledDonations(saved);
        }
        return BloodRequestResponse.from(saved);
    }

    /** Tell nearby, available, eligible, compatible donors that blood is needed. */
    private int alertCompatibleDonors(BloodRequest request) {
        List<String> groups = BloodCompatibility.donorsFor(request.getBloodGroup());
        List<Donor> candidates;
        if (request.getLatitude() != null && request.getLongitude() != null) {
            double lat = request.getLatitude().doubleValue();
            double lon = request.getLongitude().doubleValue();
            GeoUtils.BoundingBox box = GeoUtils.boundingBox(lat, lon, DONOR_ALERT_RADIUS_KM);
            candidates = donorRepository
                    .findAvailableInBox(groups, box.minLat(), box.maxLat(), box.minLon(), box.maxLon())
                    .stream()
                    .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                    .filter(d -> GeoUtils.haversineKm(d.getLatitude(), d.getLongitude(), lat, lon) <= DONOR_ALERT_RADIUS_KM)
                    .toList();
        } else {
            candidates = donorRepository.findContactable(groups, PageRequest.of(0, 50));
        }
        List<Donor> eligible = candidates.stream()
                .filter(DonorEligibility::intervalElapsed)
                .limit(MAX_DONOR_ALERTS)
                .toList();
        eligible.forEach(d -> notificationService.notify(d.getUserId(),
                        "Blood needed: " + request.getBloodGroup(),
                        request.getUnitsRequired() + " unit(s) of " + request.getBloodGroup()
                                + " are needed (" + request.getUrgency().name().toLowerCase()
                                + " urgency). Open Requests has the details — request #" + request.getId() + ".",
                        Notification.Type.REQUEST));
        return eligible.size();
    }

    /** A cancelled/rejected request releases any donor who was scheduled for it. */
    private void cancelScheduledDonations(BloodRequest request) {
        for (DonationHistory d : donationRepository.findByRequestIdAndStatus(
                request.getId(), DonationHistory.Status.SCHEDULED)) {
            d.setStatus(DonationHistory.Status.CANCELLED);
            donationRepository.save(d);
            if (d.getDonorId() != null) {
                donorRepository.findById(d.getDonorId()).ifPresent(donor ->
                        notificationService.notify(donor.getUserId(), "Donation no longer needed",
                                "Request #" + request.getId() + " was " + request.getStatus().name().toLowerCase()
                                        + ". Your scheduled donation has been cancelled. Thank you for offering.",
                                Notification.Type.DONATION));
            }
        }
    }

    private Hospital requireVerifiedHospital(Long hospitalUserId) {
        Hospital hospital = hospitalRepository.findByUserId(hospitalUserId)
                .orElseThrow(() -> new BusinessException("Complete your hospital profile first"));
        verificationService.requireFullyVerified(hospital);
        return hospital;
    }

    private void requireHospitalOwnsOrUnowned(BloodRequest request, Hospital hospital) {
        if (request.getHospitalId() != null && !request.getHospitalId().equals(hospital.getId())) {
            throw new BusinessException("This request is managed by another hospital", HttpStatus.FORBIDDEN);
        }
    }

    private void requireOwner(BloodRequest request, Long userId, String message) {
        Patient patient = patientRepository.findByUserId(userId).orElse(null);
        if (patient == null || !request.getPatientId().equals(patient.getId())) {
            throw new BusinessException(message, HttpStatus.FORBIDDEN);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

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
