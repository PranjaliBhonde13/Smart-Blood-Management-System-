package com.sben.admin.dto;

import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.hospital.dto.HospitalResponse;

import java.util.List;

/** Entities awaiting admin verification. */
public record PendingVerifications(
        List<HospitalResponse> hospitals,
        List<BloodBankResponse> bloodBanks
) {}
