package com.sben.bloodstock;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodstock.dto.BloodStockRequest;
import com.sben.bloodstock.dto.BloodStockResponse;
import com.sben.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inventory management for a blood bank's own stock. Upsert semantics keep one
 * row per (bank, group): setting a group that already exists updates its units.
 */
@Service
@RequiredArgsConstructor
public class BloodStockService {

    private final BloodStockRepository repository;
    private final BloodBankRepository bloodBankRepository;

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

    private Long requireBankId(Long userId) {
        BloodBank bank = bloodBankRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Complete your blood bank profile first"));
        return bank.getId();
    }
}
