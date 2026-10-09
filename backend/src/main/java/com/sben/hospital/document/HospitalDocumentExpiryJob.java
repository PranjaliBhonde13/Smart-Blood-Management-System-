package com.sben.hospital.document;

import com.sben.audit.AuditService;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.dto.HospitalDocumentViews.Compliance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * Nightly pass over hospital documents. Expiry already takes effect live (see
 * {@link HospitalDocument#effectiveStatus}); this job records it, so the status
 * is visible in queries and the audit trail, and sends the notifications:
 * the hospital when a document expires or is about to, and admins when an
 * expiry costs a verified hospital its verified status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HospitalDocumentExpiryJob {

    private final HospitalDocumentRepository documentRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalVerificationService verificationService;
    private final HospitalDocumentNotifier notifier;
    private final AuditService auditService;
    private final HospitalDocumentProperties properties;

    @Scheduled(cron = "${app.hospital-documents.expiry-cron:0 15 1 * * *}")
    @Transactional
    public void run() {
        int expired = markExpired(LocalDate.now());
        int warned = warnExpiringSoon(LocalDate.now());
        if (expired + warned > 0) log.info("Hospital documents: {} marked expired, {} expiry warnings sent", expired, warned);
    }

    int markExpired(LocalDate today) {
        int count = 0;
        for (HospitalDocument doc : documentRepository.findNewlyExpired(
                EnumSet.of(DocumentStatus.PENDING, DocumentStatus.VERIFIED), today)) {
            boolean wasApproved = doc.getStatus() == DocumentStatus.VERIFIED;
            doc.setStatus(DocumentStatus.EXPIRED);
            documentRepository.save(doc);
            count++;
            Hospital hospital = hospitalRepository.findById(doc.getHospitalId()).orElse(null);
            if (hospital == null) continue;
            String label = doc.getDocumentType().label();
            auditService.record("HOSPITAL_DOCUMENT_EXPIRED", "HospitalDocument", doc.getId(),
                    hospital.getHospitalName() + " — " + label + " expired on " + doc.getExpiryDate());
            Compliance c = verificationService.assess(hospital);
            boolean affectsVerification = wasApproved && hospital.isVerified()
                    && c.status() == HospitalVerificationStatus.VERIFICATION_EXPIRED;
            notifier.hospital(hospital, "Document expired",
                    label + " expired on " + doc.getExpiryDate() + " and is no longer valid. Upload the renewed document."
                            + (affectsVerification ? " Restricted request actions are disabled until it is approved." : ""));
            if (affectsVerification) {
                long active = notifier.activeRequests(hospital);
                notifier.admins("Hospital verification expired",
                        hospital.getHospitalName() + ": " + label + " expired on " + doc.getExpiryDate() + ". "
                                + (active > 0 ? active + " in-progress request(s) owned by this hospital are paused. " : "")
                                + "The hospital has been asked to upload a renewed document.");
            }
        }
        return count;
    }

    int warnExpiringSoon(LocalDate today) {
        int count = 0;
        for (HospitalDocument doc : documentRepository.findExpiringSoon(today, today.plusDays(properties.expiryWarningDays()))) {
            Hospital hospital = hospitalRepository.findById(doc.getHospitalId()).orElse(null);
            doc.setExpiryWarningSentAt(LocalDateTime.now());
            documentRepository.save(doc);
            if (hospital == null) continue;
            notifier.hospital(hospital, "Document expiring soon",
                    doc.getDocumentType().label() + " expires on " + doc.getExpiryDate()
                            + ". Upload the renewed document early so it can be reviewed before then.");
            count++;
        }
        return count;
    }
}
