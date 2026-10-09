package com.sben.hospital.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.LocalDate;

/**
 * {@code app.hospital-documents.*} settings.
 *
 * @param maxFileSize       largest accepted upload; keep it at or below spring.servlet.multipart.max-file-size
 * @param expiryWarningDays warn the hospital this many days before an approved document expires
 * @param legacyGraceUntil  optional transition window: a hospital an admin verified before documents
 *                          were introduced stays active until this date while its mandatory documents
 *                          are still missing or under review (never once one is rejected or expired)
 */
@ConfigurationProperties(prefix = "app.hospital-documents")
public record HospitalDocumentProperties(DataSize maxFileSize, Integer expiryWarningDays, LocalDate legacyGraceUntil) {

    public HospitalDocumentProperties {
        if (maxFileSize == null) maxFileSize = DataSize.ofMegabytes(5);
        if (expiryWarningDays == null) expiryWarningDays = 30;
    }

    public static HospitalDocumentProperties defaults() {
        return new HospitalDocumentProperties(null, null, null);
    }
}
