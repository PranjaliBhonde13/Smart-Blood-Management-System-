package com.sben.bloodstock.dto;

public record PublicStockSummary(
        String group,
        int totalUnits,
        String level,      // "high", "medium", "low"
        String label,      // "Available", "Moderate", "Urgent need"
        int percentage     // 15 - 100 for visual progress bar width
) {
    public static PublicStockSummary create(String group, int units) {
        String level;
        String label;
        int percentage;

        if (units >= 25) {
            level = "high";
            label = "Available";
            percentage = Math.min(100, Math.max(75, units));
        } else if (units >= 10) {
            level = "medium";
            label = "Moderate";
            percentage = Math.min(65, Math.max(40, units * 2));
        } else {
            level = "low";
            label = "Urgent need";
            percentage = Math.min(30, Math.max(15, units * 3));
        }

        return new PublicStockSummary(group, units, level, label, percentage);
    }
}
