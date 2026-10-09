package com.sben.donor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The donor's self-declared answers to a short screening questionnaire.
 *
 * <p>This is not an eligibility decision. Answers help blood centre staff, who
 * decide eligibility under the applicable guidelines at the time of donation.
 * Nothing in the system matches, ranks or blocks donors on these answers, and
 * only the donor can read them.
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HealthScreening {

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_feeling_well", length = 16)
    private ScreeningAnswer feelingWell;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_recent_illness", length = 16)
    private ScreeningAnswer recentIllness;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_current_medication", length = 16)
    private ScreeningAnswer currentMedication;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_recent_procedure", length = 16)
    private ScreeningAnswer recentProcedure;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_chronic_condition", length = 16)
    private ScreeningAnswer chronicCondition;

    /** The only question that accepts NOT_APPLICABLE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "screen_pregnancy", length = 16)
    private ScreeningAnswer pregnancy;

    @Column(name = "health_screened_at")
    private LocalDateTime screenedAt;
}
