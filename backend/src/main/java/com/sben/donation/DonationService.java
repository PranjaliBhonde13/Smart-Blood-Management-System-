package com.sben.donation;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodrequest.BloodRequest;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.BloodRequestService;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodstock.BloodStock;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.util.BloodCompatibility;
import com.sben.common.util.DonorEligibility;
import com.sben.donation.dto.DonationResponse;
import com.sben.donor.Availability;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Donation fulfilment: a donor accepts an approved request (scheduling a
 * donation), or a blood bank fulfils it from stock. Completion decrements bank
 * stock where applicable, closes the request, and notifies all parties. Request
 * status changes are delegated to {@link BloodRequestService} so the single
 * status machine remains authoritative.
 *
 * <p>Requests and stock rows are read with row locks inside each transaction, so
 * concurrent accepts/fulfilments cannot double-book a request or overspend stock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private final DonationHistoryRepository donationRepository;
    private final RequestDeclineRepository declineRepository;
    private final BloodRequestRepository requestRepository;
    private final BloodRequestService requestService;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalVerificationService verificationService;
    private final PatientRepository patientRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodStockRepository stockRepository;
    private final NotificationService notificationService;

    /** Donor commits to an APPROVED request → schedules a donation, request FULFILLED. */
    @Transactional
    public DonationResponse accept(Long requestId, Long donorUserId) {
        Donor donor = donorRepository.findByUserId(donorUserId)
                .orElseThrow(() -> new BusinessException("Complete your donor profile first"));
        BloodRequest request = lockedRequest(requestId);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BusinessException("This request is not open for donors yet",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        requireCompatible(donor, request);
        if (donor.getAvailability() != Availability.AVAILABLE) {
            throw new BusinessException("Set your availability to Available before accepting a request");
        }
        if (!DonorEligibility.intervalElapsed(donor)) {
            throw new BusinessException("You can donate again from " + DonorEligibility.nextEligibleDate(donor)
                    + " (" + DonorEligibility.MIN_DAYS_BETWEEN_DONATIONS + " days after your last donation)");
        }
        if (donationRepository.existsByRequestIdAndStatus(requestId, DonationHistory.Status.SCHEDULED)) {
            throw new BusinessException("A donor is already scheduled for this request");
        }
        DonationHistory donation = donationRepository.save(DonationHistory.builder()
                .requestId(requestId).donorId(donor.getId()).hospitalId(request.getHospitalId())
                .unitsDonated(request.getUnitsRequired()).status(DonationHistory.Status.SCHEDULED)
                .build());
        requestService.changeStatus(requestId, RequestStatus.FULFILLED);

        notifyPatient(request, "A donor accepted your request",
                "Good news — a donor has accepted your blood request #" + requestId + ".");
        notifyHospital(request, "Donor scheduled",
                "A donor was scheduled for request #" + requestId + ".");
        return DonationResponse.from(donation);
    }

    /**
     * Donor declines an open request they could have fulfilled. Recorded once per
     * donor (the request then leaves their feed); the request stays open.
     */
    @Transactional
    public void reject(Long requestId, Long donorUserId) {
        Donor donor = donorRepository.findByUserId(donorUserId)
                .orElseThrow(() -> new BusinessException("Complete your donor profile first"));
        BloodRequest request = activeRequest(requestId);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BusinessException("Only open requests can be declined");
        }
        requireCompatible(donor, request);
        if (declineRepository.existsByRequestIdAndDonorId(requestId, donor.getId())) {
            throw new BusinessException("You have already declined this request");
        }
        declineRepository.save(RequestDecline.builder().requestId(requestId).donorId(donor.getId()).build());
        log.info("Donor {} declined request {}", donor.getId(), requestId);
        notifyHospital(request, "Donor declined",
                "A donor declined request #" + requestId + ". It stays open for other donors.");
    }

    /** Owning hospital (or admin) marks the scheduled donation complete → request COMPLETED. */
    @Transactional
    public DonationResponse complete(Long requestId, Long actorUserId, Role role) {
        BloodRequest request = lockedRequest(requestId);
        if (role == Role.HOSPITAL) {
            Hospital hospital = hospitalRepository.findByUserId(actorUserId)
                    .orElseThrow(() -> new BusinessException("Complete your hospital profile first"));
            verificationService.requireFullyVerified(hospital);
            if (request.getHospitalId() != null && !request.getHospitalId().equals(hospital.getId())) {
                throw new BusinessException("This request is managed by another hospital", HttpStatus.FORBIDDEN);
            }
        }
        DonationHistory donation = donationRepository
                .findFirstByRequestIdAndStatus(requestId, DonationHistory.Status.SCHEDULED)
                .orElseThrow(() -> new BusinessException("No scheduled donation for this request"));
        donation.setStatus(DonationHistory.Status.COMPLETED);
        donation.setDonationDate(LocalDate.now());
        donationRepository.save(donation);

        // Keep the donor's eligibility data current.
        Optional.ofNullable(donation.getDonorId()).flatMap(donorRepository::findById).ifPresent(d -> {
            d.setLastDonationDate(LocalDate.now());
            d.setHasDonatedBefore(true);
            donorRepository.save(d);
        });

        requestService.changeStatus(requestId, RequestStatus.COMPLETED);

        notifyPatient(request, "Your request is fulfilled",
                "Request #" + requestId + " has been completed. Thank you.");
        notifyDonor(donation, "Thank you for donating",
                "Your donation for request #" + requestId + " is complete. You saved a life ❤️");
        return DonationResponse.from(donation);
    }

    /**
     * Verified blood bank fulfils an APPROVED request from compatible stock
     * (exact group first) → decrements units, request COMPLETED.
     */
    @Transactional
    public DonationResponse fulfillFromBank(Long requestId, Long bankUserId) {
        BloodBank bank = bloodBankRepository.findByUserId(bankUserId)
                .orElseThrow(() -> new BusinessException("Complete your blood bank profile first"));
        if (!bank.isActiveOnNetwork()) {
            throw new BusinessException(bank.isLicenseExpired()
                    ? "Your blood bank license has expired. Upload a renewed license for re-verification"
                    : "Your blood bank must be verified by an administrator first",
                    HttpStatus.FORBIDDEN);
        }
        BloodRequest request = lockedRequest(requestId);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BusinessException("This request is not ready for fulfilment",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        List<String> compatible = BloodCompatibility.donorsFor(request.getBloodGroup());
        LocalDate today = LocalDate.now();
        List<BloodStock> lines = stockRepository.lockByBankAndGroups(bank.getId(), compatible).stream()
                .filter(s -> s.getUnitsAvailable() > 0)
                .filter(s -> s.getExpiryDate() == null || !s.getExpiryDate().isBefore(today))
                // Exact group first, then other compatible groups; universal O- (the
                // scarcest, needed for unknown-type emergencies) is spent last.
                .sorted(Comparator.comparingInt((BloodStock s) -> fulfilmentPriority(s.getBloodGroup(),
                        request.getBloodGroup(), compatible)))
                .toList();
        int needed = request.getUnitsRequired();
        int available = lines.stream().mapToInt(BloodStock::getUnitsAvailable).sum();
        if (available < needed) {
            throw new BusinessException("Insufficient compatible stock: have %d, need %d"
                    .formatted(available, needed));
        }
        int remaining = needed;
        for (BloodStock line : lines) {
            if (remaining == 0) break;
            int take = Math.min(remaining, line.getUnitsAvailable());
            line.setUnitsAvailable(line.getUnitsAvailable() - take);
            stockRepository.save(line);
            remaining -= take;
        }

        DonationHistory donation = donationRepository.save(DonationHistory.builder()
                .requestId(requestId).bloodBankId(bank.getId()).hospitalId(request.getHospitalId())
                .unitsDonated(needed).donationDate(today)
                .status(DonationHistory.Status.COMPLETED).build());

        // Advance through the machine: APPROVED → FULFILLED → COMPLETED.
        requestService.changeStatus(requestId, RequestStatus.FULFILLED);
        requestService.changeStatus(requestId, RequestStatus.COMPLETED);

        notifyPatient(request, "Your request is fulfilled",
                "Request #" + requestId + " was fulfilled by " + bank.getBankName() + ".");
        return DonationResponse.from(donation);
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> myDonations(Long donorUserId) {
        Donor donor = donorRepository.findByUserId(donorUserId)
                .orElseThrow(() -> new BusinessException("Complete your donor profile first"));
        return donationRepository.findByDonorIdOrderByCreatedAtDesc(donor.getId())
                .stream().map(DonationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> byRequest(Long requestId) {
        return donationRepository.findByRequestIdOrderByCreatedAtDesc(requestId)
                .stream().map(DonationResponse::from).toList();
    }

    // ---------- helpers ----------

    private static int fulfilmentPriority(String stockGroup, String requestedGroup, List<String> compatible) {
        if (stockGroup.equals(requestedGroup)) return -1;
        if (stockGroup.equals("O-")) return Integer.MAX_VALUE;
        return compatible.indexOf(stockGroup);
    }

    private void requireCompatible(Donor donor, BloodRequest request) {
        if (!BloodCompatibility.donorsFor(request.getBloodGroup()).contains(donor.getBloodGroup())) {
            throw new BusinessException("Your blood group (%s) is not compatible with this request (%s)"
                    .formatted(donor.getBloodGroup(), request.getBloodGroup()));
        }
    }

    /** Active request, row-locked for the rest of the transaction. */
    private BloodRequest lockedRequest(Long id) {
        BloodRequest r = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood request", id));
        if (r.isDeleted()) throw new ResourceNotFoundException("Blood request", id);
        return r;
    }

    private BloodRequest activeRequest(Long id) {
        BloodRequest r = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood request", id));
        if (r.isDeleted()) throw new ResourceNotFoundException("Blood request", id);
        return r;
    }

    private void notifyPatient(BloodRequest request, String title, String message) {
        patientRepository.findById(request.getPatientId())
                .map(p -> p.getUserId())
                .ifPresent(uid -> notificationService.notify(uid, title, message, Notification.Type.REQUEST));
    }

    private void notifyHospital(BloodRequest request, String title, String message) {
        Optional.ofNullable(request.getHospitalId())
                .flatMap(hospitalRepository::findById)
                .map(h -> h.getUserId())
                .ifPresent(uid -> notificationService.notify(uid, title, message, Notification.Type.DONATION));
    }

    private void notifyDonor(DonationHistory donation, String title, String message) {
        Optional.ofNullable(donation.getDonorId())
                .flatMap(donorRepository::findById)
                .map(d -> d.getUserId())
                .ifPresent(uid -> notificationService.notify(uid, title, message, Notification.Type.DONATION));
    }
}
