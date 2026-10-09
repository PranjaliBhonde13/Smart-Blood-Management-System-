package com.sben.hospital;

import com.sben.common.exception.BusinessException;
import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.hospital.document.HospitalDocumentNotifier;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.hospital.document.dto.HospitalDocumentViews.Compliance;
import com.sben.hospital.dto.HospitalRequest;
import com.sben.hospital.dto.HospitalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Manages the caller's own hospital profile. Verification is admin-only (M6). */
@Service
@RequiredArgsConstructor
public class HospitalService {

    private final HospitalRepository repository;
    private final HospitalVerificationService verificationService;
    private final HospitalDocumentNotifier notifier;

    @Transactional(readOnly = true)
    public HospitalResponse getMyProfile(Long userId) {
        Hospital hospital = repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital profile not found. Complete your profile."));
        return HospitalResponse.from(hospital, verificationService.assess(hospital));
    }

    @Transactional
    public HospitalResponse upsertMyProfile(Long userId, HospitalRequest req) {
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new FieldValidationException(Map.of(req.latitude() == null ? "latitude" : "longitude",
                    "enter both latitude and longitude, or leave both empty"));
        }
        Hospital hospital = repository.findByUserId(userId).orElse(null);
        boolean wasFullyVerified = false;
        if (hospital == null) {
            // License must be unique across hospitals on first registration.
            if (repository.existsByLicenseNumber(req.licenseNumber())) {
                throw new BusinessException("License number already registered", HttpStatus.CONFLICT);
            }
            hospital = Hospital.builder().userId(userId).verified(false).build();
        } else {
            if (repository.existsByLicenseNumberAndIdNot(req.licenseNumber(), hospital.getId())) {
                throw new BusinessException("License number already registered", HttpStatus.CONFLICT);
            }
            wasFullyVerified = verificationService.isFullyVerified(hospital);
            // Identity changed after verification -> needs admin review again.
            if (hospital.isVerified() && (!req.hospitalName().equals(hospital.getHospitalName())
                    || !req.licenseNumber().equals(hospital.getLicenseNumber()))) {
                hospital.setVerified(false);
                hospital.setVerifiedAt(null);
                hospital.setVerifiedBy(null);
            }
        }
        if (hospital.getRejectedAt() != null) {
            hospital.setRejectedAt(null);
            hospital.setRejectionReason(null);
        }
        hospital.setHospitalName(req.hospitalName());
        hospital.setLicenseNumber(req.licenseNumber());
        hospital.setPhone(normalisePhone(req.phone()));
        hospital.setAddress(trimToNull(req.address()));
        hospital.setCity(trimToNull(req.city()));
        hospital.setState(trimToNull(req.state()));
        hospital.setPincode(trimToNull(req.pincode()));
        hospital.setLatitude(coordinate(req.latitude()));
        hospital.setLongitude(coordinate(req.longitude()));
        hospital.setEmergencyPhone(normalisePhone(req.emergencyPhone()));
        hospital.setOfficialEmail(trimToNull(req.officialEmail()));
        hospital.setContactPersonName(trimToNull(req.contactPersonName()));
        hospital.setContactPersonDesignation(trimToNull(req.contactPersonDesignation()));
        // Category and blood-bank flag decide which documents apply (e.g. a blood bank license).
        if (req.registrationCategory() != null) hospital.setRegistrationCategory(req.registrationCategory());
        if (req.operatesBloodBank() != null) hospital.setOperatesBloodBank(req.operatesBloodBank());

        Hospital saved = repository.save(hospital);
        Compliance after = verificationService.assess(saved);
        if (wasFullyVerified && !after.fullyVerified()) {
            notifier.verificationLost(saved, after, "profile details changed");
        }
        return HospitalResponse.from(saved, after);
    }

    /**
     * Stores a number as +91 followed by its 10 digits (landline numbers keep
     * their STD code). Values that are not 10-digit Indian numbers, such as
     * rows saved before validation existed, are kept as entered.
     */
    static String normalisePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        else if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        return digits.length() == 10 ? "+91" + digits : phone.trim();
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value == null ? null : value.setScale(7, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
