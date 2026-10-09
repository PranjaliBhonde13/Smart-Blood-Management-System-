package com.sben.common.util;

import com.sben.donor.Availability;
import com.sben.donor.Donor;

import java.time.LocalDate;

/**
 * Who may donate right now. Whole-blood donors must wait
 * {@link #MIN_DAYS_BETWEEN_DONATIONS} days between donations and must have
 * marked themselves available.
 */
public final class DonorEligibility {

    public static final int MIN_DAYS_BETWEEN_DONATIONS = 90;

    private DonorEligibility() {}

    /** True when enough time has passed since the donor's last donation. */
    public static boolean intervalElapsed(Donor donor) {
        LocalDate last = donor.getLastDonationDate();
        return last == null || !last.isAfter(LocalDate.now().minusDays(MIN_DAYS_BETWEEN_DONATIONS));
    }

    /** First date the donor may donate again. */
    public static LocalDate nextEligibleDate(Donor donor) {
        LocalDate last = donor.getLastDonationDate();
        return last == null ? LocalDate.now() : last.plusDays(MIN_DAYS_BETWEEN_DONATIONS);
    }

    public static boolean canDonateNow(Donor donor) {
        return donor.getAvailability() == Availability.AVAILABLE && intervalElapsed(donor);
    }
}
