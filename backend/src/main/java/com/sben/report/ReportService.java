package com.sben.report;

import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodrequest.BloodRequest;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.donation.DonationHistory;
import com.sben.common.exception.BusinessException;
import com.sben.donation.DonationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only reporting aggregates and CSV export. Summaries feed the admin
 * analytics view; the CSV export produces a downloadable operational report.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final BloodRequestRepository requestRepository;
    private final DonationHistoryRepository donationRepository;
    private final BloodStockRepository stockRepository;
    private final BloodBankRepository bloodBankRepository;

    @Transactional(readOnly = true)
    public Map<String, Long> requestsSummary() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (RequestStatus s : RequestStatus.values()) {
            out.put(s.name(), requestRepository.countByStatusAndDeletedFalse(s));
        }
        out.put("TOTAL", requestRepository.countByDeletedFalse());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> donationsSummary() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (DonationHistory.Status s : DonationHistory.Status.values()) {
            out.put(s.name(), donationRepository.countByStatus(s));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> inventorySummary() {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("totalUnits", stockRepository.totalUnits());
        out.put("stockLines", stockRepository.countByDeletedFalse());
        return out;
    }

    /** Inventory summary for one blood bank (its own stock only). */
    @Transactional(readOnly = true)
    public Map<String, Long> inventorySummaryForBankUser(Long bankUserId) {
        Long bankId = bloodBankRepository.findByUserId(bankUserId)
                .orElseThrow(() -> new BusinessException("Complete your blood bank profile first"))
                .getId();
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("totalUnits", stockRepository.totalUnitsForBank(bankId));
        out.put("stockLines", stockRepository.countByBloodBankIdAndDeletedFalse(bankId));
        return out;
    }

    /** CSV of recent requests (capped) for download. */
    @Transactional(readOnly = true)
    public String exportRequestsCsv() {
        StringBuilder sb = new StringBuilder("id,patientId,hospitalId,bloodGroup,unitsRequired,urgency,status,requestDate\n");
        for (BloodRequest r : requestRepository.findTop500ByDeletedFalseOrderByRequestDateDesc()) {
            sb.append(r.getId()).append(',')
              .append(r.getPatientId()).append(',')
              .append(r.getHospitalId() == null ? "" : r.getHospitalId()).append(',')
              .append(r.getBloodGroup()).append(',')
              .append(r.getUnitsRequired()).append(',')
              .append(r.getUrgency()).append(',')
              .append(r.getStatus()).append(',')
              .append(r.getRequestDate()).append('\n');
        }
        return sb.toString();
    }
}
