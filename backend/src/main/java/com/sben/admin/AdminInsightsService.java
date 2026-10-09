package com.sben.admin;

import com.sben.admin.dto.AdminViews.Analytics;
import com.sben.admin.dto.AdminViews.BankInventory;
import com.sben.admin.dto.AdminViews.DayPoint;
import com.sben.admin.dto.AdminViews.DonorPool;
import com.sben.admin.dto.AdminViews.GroupSupply;
import com.sben.admin.dto.AdminViews.HospitalPerformance;
import com.sben.admin.dto.AdminViews.NetworkInventory;
import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodrequest.BloodRequest;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodstock.BloodStock;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.common.util.DonorEligibility;
import com.sben.donor.Availability;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only network views for admins: inventory, donor pool and request analytics. */
@Service
@RequiredArgsConstructor
public class AdminInsightsService {

    static final List<String> GROUPS = List.of("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-");
    static final int LOW_STOCK = 5;
    static final int EXPIRY_WARNING_DAYS = 7;

    private final BloodBankRepository bankRepository;
    private final BloodStockRepository stockRepository;
    private final DonorRepository donorRepository;
    private final BloodRequestRepository requestRepository;
    private final HospitalRepository hospitalRepository;

    /** Stock per bank per group, network totals per group, and the donor pool. */
    @Transactional(readOnly = true)
    public NetworkInventory inventory() {
        LocalDate today = LocalDate.now();
        LocalDate warnBy = today.plusDays(EXPIRY_WARNING_DAYS);
        Map<Long, List<BloodStock>> byBank = stockRepository.findAll().stream()
                .filter(s -> !s.isDeleted())
                .collect(Collectors.groupingBy(BloodStock::getBloodBankId));

        List<BankInventory> banks = bankRepository.findAll().stream()
                .filter(b -> !b.isDeleted())
                .map(b -> bankRow(b, byBank.getOrDefault(b.getId(), List.of()), today, warnBy))
                .sorted(Comparator.comparing(BankInventory::verified).reversed()
                        .thenComparing(BankInventory::bankName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<GroupSupply> groups = GROUPS.stream().map(g -> {
            int units = 0, holding = 0;
            for (BankInventory b : banks) {
                if (!b.verified()) continue;
                int u = b.units().getOrDefault(g, 0);
                units += u;
                if (u > 0) holding++;
            }
            String level = units >= 25 ? "high" : units >= 10 ? "medium" : "low";
            return new GroupSupply(g, units, holding, level);
        }).toList();

        List<Donor> donors = donorRepository.findAll().stream().filter(d -> !d.isDeleted()).toList();
        List<DonorPool> pool = GROUPS.stream().map(g -> {
            List<Donor> ofGroup = donors.stream().filter(d -> g.equals(d.getBloodGroup())).toList();
            long available = ofGroup.stream().filter(d -> d.getAvailability() == Availability.AVAILABLE).count();
            long eligible = ofGroup.stream().filter(DonorEligibility::canDonateNow).count();
            return new DonorPool(g, ofGroup.size(), available, eligible);
        }).toList();

        return new NetworkInventory(groups, banks, pool, LOW_STOCK, EXPIRY_WARNING_DAYS);
    }

    private BankInventory bankRow(BloodBank b, List<BloodStock> lines, LocalDate today, LocalDate warnBy) {
        Map<String, Integer> units = new LinkedHashMap<>();
        GROUPS.forEach(g -> units.put(g, 0));
        int expiring = 0, expired = 0;
        LocalDate nextExpiry = null;
        LocalDateTime lastUpdated = null;
        for (BloodStock s : lines) {
            boolean isExpired = s.getExpiryDate() != null && s.getExpiryDate().isBefore(today);
            if (isExpired) {
                expired += s.getUnitsAvailable();
            } else {
                units.merge(s.getBloodGroup(), s.getUnitsAvailable(), Integer::sum);
                if (s.getExpiryDate() != null && !s.getExpiryDate().isAfter(warnBy) && s.getUnitsAvailable() > 0) {
                    expiring += s.getUnitsAvailable();
                }
                if (s.getExpiryDate() != null && s.getUnitsAvailable() > 0
                        && (nextExpiry == null || s.getExpiryDate().isBefore(nextExpiry))) {
                    nextExpiry = s.getExpiryDate();
                }
            }
            if (s.getLastUpdated() != null && (lastUpdated == null || s.getLastUpdated().isAfter(lastUpdated))) {
                lastUpdated = s.getLastUpdated();
            }
        }
        List<String> low = GROUPS.stream().filter(g -> units.get(g) <= LOW_STOCK).toList();
        int total = units.values().stream().mapToInt(Integer::intValue).sum();
        return new BankInventory(b.getId(), b.getBankName(), b.getCity(), b.isVerified(), units, total,
                low, expiring, expired, nextExpiry, lastUpdated);
    }

    /** Request trends over the last {@code days} days. */
    @Transactional(readOnly = true)
    public Analytics analytics(int days) {
        int window = Math.max(1, Math.min(days, 365));
        LocalDate firstDay = LocalDate.now().minusDays(window - 1L);
        List<BloodRequest> requests = requestRepository.findByRequestDateGreaterThanEqualAndDeletedFalse(firstDay.atStartOfDay());

        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (int i = 0; i < window; i++) perDay.put(firstDay.plusDays(i), new long[2]);
        long completed = 0, rejected = 0, cancelled = 0, open = 0;
        List<Double> hoursToComplete = new ArrayList<>();
        for (BloodRequest r : requests) {
            long[] created = perDay.get(r.getRequestDate().toLocalDate());
            if (created != null) created[0]++;
            switch (r.getStatus()) {
                case COMPLETED -> {
                    completed++;
                    LocalDateTime done = r.getUpdatedAt();
                    if (done != null) {
                        long[] d = perDay.get(done.toLocalDate());
                        if (d != null) d[1]++;
                        hoursToComplete.add(Duration.between(r.getRequestDate(), done).toMinutes() / 60.0);
                    }
                }
                case REJECTED -> rejected++;
                case CANCELLED -> cancelled++;
                default -> open++;
            }
        }
        List<DayPoint> series = perDay.entrySet().stream()
                .map(e -> new DayPoint(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        Map<String, Long> byUrgency = new LinkedHashMap<>();
        for (var u : com.sben.bloodrequest.Urgency.values()) {
            byUrgency.put(u.name(), requests.stream().filter(r -> r.getUrgency() == u).count());
        }
        Map<String, Long> byGroup = new LinkedHashMap<>();
        GROUPS.forEach(g -> byGroup.put(g, requests.stream().filter(r -> g.equals(r.getBloodGroup())).count()));

        Map<Long, Hospital> hospitals = hospitalRepository.findAll().stream()
                .collect(Collectors.toMap(Hospital::getId, Function.identity()));
        List<HospitalPerformance> perHospital = requests.stream()
                .filter(r -> r.getHospitalId() != null)
                .collect(Collectors.groupingBy(BloodRequest::getHospitalId))
                .entrySet().stream()
                .map(e -> {
                    List<BloodRequest> rs = e.getValue();
                    List<Double> hrs = rs.stream()
                            .filter(r -> r.getStatus() == RequestStatus.COMPLETED && r.getUpdatedAt() != null)
                            .map(r -> Duration.between(r.getRequestDate(), r.getUpdatedAt()).toMinutes() / 60.0)
                            .toList();
                    Hospital h = hospitals.get(e.getKey());
                    return new HospitalPerformance(e.getKey(), h == null ? "Hospital #" + e.getKey() : h.getHospitalName(),
                            rs.size(),
                            rs.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).count(),
                            rs.stream().filter(r -> r.getStatus() == RequestStatus.REJECTED).count(),
                            average(hrs));
                })
                .sorted(Comparator.comparingLong(HospitalPerformance::handled).reversed())
                .toList();

        long closed = completed + rejected + cancelled;
        Double rate = closed == 0 ? null : round(100.0 * completed / closed);
        return new Analytics(window, requests.size(), completed, rejected, cancelled, open,
                rate, average(hoursToComplete), series, byUrgency, byGroup, perHospital);
    }

    private static Double average(List<Double> values) {
        return values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
