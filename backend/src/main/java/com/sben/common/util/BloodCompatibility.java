package com.sben.common.util;

import java.util.List;
import java.util.Map;

/**
 * Blood-group compatibility rules (see docs/03-DATABASE.md §2).
 *
 * <p>{@link #donorsFor(String)} returns the groups whose blood a recipient of a
 * given group can safely receive — the exact set used to match a request to
 * eligible donors and to compatible bank stock.
 */
public final class BloodCompatibility {

    private BloodCompatibility() {}

    /** recipient group → groups they can receive from. */
    private static final Map<String, List<String>> CAN_RECEIVE_FROM = Map.of(
            "O-",  List.of("O-"),
            "O+",  List.of("O-", "O+"),
            "A-",  List.of("O-", "A-"),
            "A+",  List.of("O-", "O+", "A-", "A+"),
            "B-",  List.of("O-", "B-"),
            "B+",  List.of("O-", "O+", "B-", "B+"),
            "AB-", List.of("O-", "A-", "B-", "AB-"),
            "AB+", List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")
    );

    /** Donor groups compatible with a recipient of {@code recipientGroup}. */
    public static List<String> donorsFor(String recipientGroup) {
        List<String> groups = CAN_RECEIVE_FROM.get(recipientGroup);
        if (groups == null) {
            throw new IllegalArgumentException("Unknown blood group: " + recipientGroup);
        }
        return groups;
    }

    /**
     * Recipient groups a donor of {@code donorGroup} can give to — the reverse
     * of {@link #donorsFor}. Used to surface requests a donor can fulfil.
     */
    public static List<String> recipientsFor(String donorGroup) {
        if (!CAN_RECEIVE_FROM.containsKey(donorGroup)) {
            throw new IllegalArgumentException("Unknown blood group: " + donorGroup);
        }
        return CAN_RECEIVE_FROM.entrySet().stream()
                .filter(e -> e.getValue().contains(donorGroup))
                .map(Map.Entry::getKey)
                .toList();
    }
}
