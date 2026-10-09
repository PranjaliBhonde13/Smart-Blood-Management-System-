package com.sben.bloodbank;

import com.sben.audit.AuditService;
import com.sben.bloodbank.dto.BloodBankRequest;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the caller's own blood bank profile, license certificate and logo.
 * Verification decisions are admin-only ({@code AdminService}); this service
 * only ever moves a license back into the review queue, never out of it.
 *
 * <p>Re-verification rules: a new/replaced license document, or a change to the
 * bank name, license number or license dates, clears any previous decision. Any
 * save after a rejection is treated as a resubmission.
 */
@Service
@RequiredArgsConstructor
public class BloodBankService {

    static final Set<String> LICENSE_TYPES = Set.of("pdf", "png", "jpg", "jpeg");
    static final long LICENSE_MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    static final Set<String> LOGO_TYPES = Set.of("png", "jpg", "jpeg");
    static final long LOGO_MAX_BYTES = 2L * 1024 * 1024;    // 2 MB

    private final BloodBankRepository repository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BloodBankResponse getMyProfile(Long userId) {
        return BloodBankResponse.from(requireMine(userId));
    }

    @Transactional
    public BloodBankResponse upsertMyProfile(Long userId, BloodBankRequest req) {
        String licenseNumber = req.licenseNumber().trim();
        if (req.licenseIssueDate() != null && req.licenseExpiryDate() != null
                && !req.licenseExpiryDate().isAfter(req.licenseIssueDate())) {
            throw new BusinessException("License expiry date must be after the issue date", HttpStatus.BAD_REQUEST);
        }

        BloodBank bank = repository.findByUserId(userId).orElse(null);
        boolean resubmit = false;
        if (bank == null) {
            if (repository.existsByLicenseNumber(licenseNumber)) {
                throw new BusinessException("License number already registered", HttpStatus.CONFLICT);
            }
            bank = BloodBank.builder().userId(userId).verified(false).build();
        } else {
            if (repository.existsByLicenseNumberAndIdNot(licenseNumber, bank.getId())) {
                throw new BusinessException("License number already registered", HttpStatus.CONFLICT);
            }
            boolean identityChanged = !req.bankName().trim().equals(bank.getBankName())
                    || !licenseNumber.equals(bank.getLicenseNumber())
                    || !Objects.equals(req.licenseIssueDate(), bank.getLicenseIssueDate())
                    || !Objects.equals(req.licenseExpiryDate(), bank.getLicenseExpiryDate());
            // A decision (verified or rejected) was made on the old details -> review again.
            resubmit = (identityChanged && (bank.isVerified() || bank.getRejectedAt() != null))
                    || bank.getRejectedAt() != null;
        }

        bank.setBankName(req.bankName().trim());
        bank.setContactEmail(req.contactEmail().trim().toLowerCase());
        bank.setPhone(req.phone().trim());
        bank.setAlternatePhone(blankToNull(req.alternatePhone()));
        bank.setWebsite(blankToNull(req.website()));
        bank.setAddress(req.address().trim());
        bank.setState(req.state().trim());
        bank.setCity(req.city().trim());
        bank.setPincode(req.pincode().trim());
        bank.setLatitude(req.latitude());
        bank.setLongitude(req.longitude());
        bank.setLicenseNumber(licenseNumber);
        bank.setLicenseIssueDate(req.licenseIssueDate());
        bank.setLicenseExpiryDate(req.licenseExpiryDate());

        if (resubmit) {
            bank.resubmitForVerification();
            if (bank.getLicenseDocument() == null) bank.setLicenseSubmittedAt(null);
        }
        BloodBank saved = repository.save(bank);
        if (resubmit && saved.getLicenseDocument() != null) {
            auditService.record("BLOOD_BANK_LICENSE_RESUBMITTED", "BloodBank", saved.getId(),
                    saved.getBankName() + " — license details changed");
            notifyAdmins(saved, "updated its license details");
        }
        return BloodBankResponse.from(saved);
    }

    /**
     * Upload or replace the license certificate. Always (re)enters PENDING review —
     * a replaced document must be checked again even if the bank was verified.
     */
    @Transactional
    public BloodBankResponse uploadLicenseDocument(Long userId, MultipartFile file) {
        BloodBank bank = requireMine(userId);
        String stored = fileStorageService.store(file, LICENSE_TYPES, LICENSE_MAX_BYTES);
        boolean replacing = bank.getLicenseDocument() != null;
        // The previous certificate is kept on disk (unreferenced) as a compliance record.
        bank.setLicenseDocument(stored);
        bank.setLicenseDocumentName(displayName(file.getOriginalFilename()));
        bank.setLicenseDocumentType(contentType(stored));
        bank.setLicenseDocumentSize(file.getSize());
        bank.resubmitForVerification();
        BloodBank saved = repository.save(bank);
        auditService.record(replacing ? "BLOOD_BANK_LICENSE_REPLACED" : "BLOOD_BANK_LICENSE_SUBMITTED",
                "BloodBank", saved.getId(), saved.getBankName() + " — " + saved.getLicenseDocumentName());
        notifyAdmins(saved, replacing ? "replaced its license certificate" : "submitted its license certificate");
        return BloodBankResponse.from(saved);
    }

    /** Upload or replace the optional logo. Cosmetic only: does not affect verification. */
    @Transactional
    public BloodBankResponse uploadLogo(Long userId, MultipartFile file) {
        BloodBank bank = requireMine(userId);
        String stored = fileStorageService.store(file, LOGO_TYPES, LOGO_MAX_BYTES);
        String previous = bank.getLogoFile();
        bank.setLogoFile(stored);
        BloodBank saved = repository.save(bank);
        fileStorageService.delete(previous);
        return BloodBankResponse.from(saved);
    }

    @Transactional
    public BloodBankResponse removeLogo(Long userId) {
        BloodBank bank = requireMine(userId);
        String previous = bank.getLogoFile();
        bank.setLogoFile(null);
        BloodBank saved = repository.save(bank);
        fileStorageService.delete(previous);
        return BloodBankResponse.from(saved);
    }

    /** The caller's own license certificate. */
    @Transactional(readOnly = true)
    public StoredFile myLicenseDocument(Long userId) {
        return licenseDocument(requireMine(userId));
    }

    /** Any bank's license certificate — callers must already be authorised as ADMIN. */
    @Transactional(readOnly = true)
    public StoredFile licenseDocumentForAdmin(Long bankId) {
        BloodBank bank = repository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank", bankId));
        return licenseDocument(bank);
    }

    @Transactional(readOnly = true)
    public StoredFile myLogo(Long userId) {
        BloodBank bank = requireMine(userId);
        if (bank.getLogoFile() == null) throw new ResourceNotFoundException("No logo uploaded");
        return new StoredFile(fileStorageService.load(bank.getLogoFile()), contentType(bank.getLogoFile()), "logo");
    }

    /** A resolved private file plus the headers needed to stream it. */
    public record StoredFile(Path path, String contentType, String downloadName) {}

    // ---------- helpers ----------

    private StoredFile licenseDocument(BloodBank bank) {
        if (bank.getLicenseDocument() == null) {
            throw new ResourceNotFoundException("No license certificate uploaded");
        }
        String name = bank.getLicenseDocumentName() != null ? bank.getLicenseDocumentName()
                : "license" + extension(bank.getLicenseDocument());
        return new StoredFile(fileStorageService.load(bank.getLicenseDocument()),
                contentType(bank.getLicenseDocument()), name);
    }

    private BloodBank requireMine(Long userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank profile not found. Complete your profile."));
    }

    /** In-app + email heads-up to every active admin that a license awaits review. */
    private void notifyAdmins(BloodBank bank, String what) {
        for (User admin : userRepository.findByRoleAndDeletedFalse(Role.ADMIN, Pageable.unpaged())) {
            if (!admin.isEnabled()) continue;
            notificationService.notify(admin.getId(), "Blood bank license awaiting review",
                    bank.getBankName() + " " + what + ". Review it under Verifications.",
                    Notification.Type.VERIFICATION);
        }
    }

    /** Keep only the base name, strip control/quote characters, cap the length. */
    static String displayName(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "" : original));
        if (name == null || name.isBlank()) return "license";
        name = name.replaceAll("[\\p{Cntrl}\"\\\\/<>]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String contentType(String storedName) {
        return switch (extension(storedName)) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
