package com.sben.bloodstock;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodstock.dto.BloodStockRequest;
import com.sben.bloodstock.dto.BloodStockResponse;
import com.sben.bloodstock.dto.PublicStockSummary;
import com.sben.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inventory management for a blood bank's own stock. Upsert semantics keep one
 * row per (bank, group): setting a group that already exists updates its units.
 */
@Service
@RequiredArgsConstructor
public class BloodStockService {

    private final BloodStockRepository repository;
    private final BloodBankRepository bloodBankRepository;

    private static final List<String> BLOOD_GROUPS =
            List.of("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-");

    @Transactional(readOnly = true)
    public List<BloodStockResponse> getMyStock(Long userId) {
        Long bankId = requireBankId(userId);
        return repository.findByBloodBankIdAndDeletedFalse(bankId)
                .stream().map(BloodStockResponse::from).toList();
    }

    @Transactional
    public BloodStockResponse upsert(Long userId, BloodStockRequest req) {
        Long bankId = requireBankId(userId);
        BloodStock stock = repository
                .findByBloodBankIdAndBloodGroupAndDeletedFalse(bankId, req.bloodGroup())
                .orElseGet(() -> BloodStock.builder()
                        .bloodBankId(bankId).bloodGroup(req.bloodGroup()).build());
        stock.setUnitsAvailable(req.unitsAvailable());
        stock.setExpiryDate(req.expiryDate());
        return BloodStockResponse.from(repository.save(stock));
    }

    /**
     * Public, anonymous availability per blood group: units held by verified
     * banks in unexpired stock, bucketed into high/medium/low for the landing page.
     */
    @Transactional(readOnly = true)
    public List<PublicStockSummary> publicSummary() {
        Set<Long> verifiedBanks = bloodBankRepository.findAll().stream()
                .filter(b -> b.isActiveOnNetwork() && !b.isDeleted())
                .map(BloodBank::getId)
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        Map<String, Integer> units = new HashMap<>();
        for (BloodStock s : repository.findAll()) {
            if (s.isDeleted() || !verifiedBanks.contains(s.getBloodBankId())) continue;
            if (s.getExpiryDate() != null && s.getExpiryDate().isBefore(today)) continue;
            units.merge(s.getBloodGroup(), s.getUnitsAvailable(), Integer::sum);
        }
        return BLOOD_GROUPS.stream()
                .map(g -> PublicStockSummary.create(g, units.getOrDefault(g, 0)))
                .toList();
    }

    private Long requireBankId(Long userId) {
        BloodBank bank = bloodBankRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Complete your blood bank profile first"));
        return bank.getId();
    }
}
