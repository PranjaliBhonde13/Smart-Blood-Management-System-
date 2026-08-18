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
import com.sben.donation.dto.DonationResponse;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.HospitalRepository;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Donation fulfilment: a donor accepts an approved request (scheduling a
 * donation), or a blood bank fulfils it from stock. Completion decrements bank
 * stock where applicable, closes the request, and notifies all parties. Request
 * status changes are delegated to {@link BloodRequestService} so the single
 * status machine remains authoritative.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private final DonationHistoryRepository donationRepository;
    private final BloodRequestRepository requestRepository;
    private final BloodRequestService requestService;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final PatientRepository patientRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodStockRepository stockRepository;
    private final NotificationService notificationService;

    /** Donor commits to an APPROVED request → schedules a donation, request FULFILLED. */
    @Transactional
    public DonationResponse accept(Long requestId, Long donorUserId) {
        Donor donor = donorRepository.findByUserId(donorUserId)
                .orElseThrow(() -> new BusinessException("Complete your donor profile first"));
        BloodRequest request = activeRequest(requestId);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BusinessException("This request is not open for donors yet",
                    HttpStatus.UNPROCESSABLE_ENTITY);
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

    /** Donor declines — recorded for audit; no state change to the request. */
    @Transactional
    public void reject(Long requestId, Long donorUserId) {
        BloodRequest request = activeRequest(requestId);
        log.info("Donor (user {}) declined request {}", donorUserId, requestId);
        notifyHospital(request, "Donor declined",
                "A donor declined request #" + requestId + ". Keep it open for others.");
    }

    /** Hospital/admin marks the scheduled donation complete → request COMPLETED. */
    @Transactional
    public DonationResponse complete(Long requestId) {
        DonationHistory donation = donationRepository
                .findFirstByRequestIdAndStatus(requestId, DonationHistory.Status.SCHEDULED)
                .orElseThrow(() -> new BusinessException("No scheduled donation for this request"));
        donation.setStatus(DonationHistory.Status.COMPLETED);
        donation.setDonationDate(LocalDate.now());
        donationRepository.save(donation);

        BloodRequest request = activeRequest(requestId);
        requestService.changeStatus(requestId, RequestStatus.COMPLETED);

        notifyPatient(request, "Your request is fulfilled",
                "Request #" + requestId + " has been completed. Thank you.");
        notifyDonor(donation, "Thank you for donating",
                "Your donation for request #" + requestId + " is complete. You saved a life ❤️");
        return DonationResponse.from(donation);
    }

    /** Blood bank fulfils an APPROVED request from stock → decrements units, request COMPLETED. */
    @Transactional
    public DonationResponse fulfillFromBank(Long requestId, Long bankUserId) {
        BloodBank bank = bloodBankRepository.findByUserId(bankUserId)
                .orElseThrow(() -> new BusinessException("Complete your blood bank profile first"));
        BloodRequest request = activeRequest(requestId);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BusinessException("This request is not ready for fulfilment",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        BloodStock stock = stockRepository
                .findByBloodBankIdAndBloodGroupAndDeletedFalse(bank.getId(), request.getBloodGroup())
                .orElseThrow(() -> new BusinessException(
                        "You have no stock of " + request.getBloodGroup()));
        if (stock.getUnitsAvailable() < request.getUnitsRequired()) {
            throw new BusinessException("Insufficient stock: have %d, need %d"
                    .formatted(stock.getUnitsAvailable(), request.getUnitsRequired()));
        }
        stock.setUnitsAvailable(stock.getUnitsAvailable() - request.getUnitsRequired());
        stockRepository.save(stock);

        DonationHistory donation = donationRepository.save(DonationHistory.builder()
                .requestId(requestId).bloodBankId(bank.getId()).hospitalId(request.getHospitalId())
                .unitsDonated(request.getUnitsRequired()).donationDate(LocalDate.now())
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
