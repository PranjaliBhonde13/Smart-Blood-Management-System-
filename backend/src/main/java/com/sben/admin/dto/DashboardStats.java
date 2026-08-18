package com.sben.admin.dto;

import java.util.Map;

/** Aggregated KPIs for the admin dashboard. */
public record DashboardStats(
        long totalUsers,
        long patients,
        long donors,
        long hospitals,
        long bloodBanks,
        long pendingHospitalVerifications,
        long pendingBankVerifications,
        long totalRequests,
        long pendingRequests,
        long completedRequests,
        long requestsToday,
        long completedDonations,
        long totalUnitsInStock,
        Map<String, Long> requestsByStatus
) {}
