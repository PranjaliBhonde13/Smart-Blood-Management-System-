package com.sben.donation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A donor's "no" to a specific request. Stored once per (request, donor) so a
 * decline is recorded a single time and the request drops out of that donor's
 * open-requests feed.
 */
@Entity
@Table(name = "request_declines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RequestDecline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
