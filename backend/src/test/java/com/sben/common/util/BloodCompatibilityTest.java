package com.sben.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the clinical blood-group compatibility rules used by matching. */
class BloodCompatibilityTest {

    @Test
    void oNegativeIsUniversalDonor_soEveryRecipientCanReceiveFromIt() {
        for (String recipient : List.of("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")) {
            assertTrue(BloodCompatibility.donorsFor(recipient).contains("O-"),
                    recipient + " must be able to receive O-");
        }
    }

    @Test
    void abPositiveIsUniversalRecipient() {
        assertEquals(8, BloodCompatibility.donorsFor("AB+").size(),
                "AB+ can receive from all 8 groups");
    }

    @Test
    void oNegativeCanOnlyReceiveONegative() {
        assertEquals(List.of("O-"), BloodCompatibility.donorsFor("O-"));
    }

    @Test
    void aPositiveReceivesFromOandA() {
        assertEquals(List.of("O-", "O+", "A-", "A+"), BloodCompatibility.donorsFor("A+"));
    }

    @Test
    void recipientsForIsInverseOfDonorsFor() {
        // If X can donate to Y, then Y must list X among its donors.
        for (String donor : List.of("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")) {
            for (String recipient : BloodCompatibility.recipientsFor(donor)) {
                assertTrue(BloodCompatibility.donorsFor(recipient).contains(donor),
                        donor + " -> " + recipient + " must be symmetric");
            }
        }
    }

    @Test
    void unknownGroupThrows() {
        assertThrows(IllegalArgumentException.class, () -> BloodCompatibility.donorsFor("XY"));
    }
}
