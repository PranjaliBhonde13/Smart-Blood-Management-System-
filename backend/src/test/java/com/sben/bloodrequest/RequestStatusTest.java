package com.sben.bloodrequest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the request lifecycle state machine (legal vs illegal transitions). */
class RequestStatusTest {

    @Test
    void happyPathTransitionsAreLegal() {
        assertTrue(RequestStatus.PENDING.canTransitionTo(RequestStatus.VERIFIED));
        assertTrue(RequestStatus.VERIFIED.canTransitionTo(RequestStatus.APPROVED));
        assertTrue(RequestStatus.APPROVED.canTransitionTo(RequestStatus.FULFILLED));
        assertTrue(RequestStatus.FULFILLED.canTransitionTo(RequestStatus.COMPLETED));
    }

    @Test
    void skippingStepsIsIllegal() {
        assertFalse(RequestStatus.PENDING.canTransitionTo(RequestStatus.COMPLETED));
        assertFalse(RequestStatus.PENDING.canTransitionTo(RequestStatus.APPROVED));
        assertFalse(RequestStatus.VERIFIED.canTransitionTo(RequestStatus.FULFILLED));
    }

    @Test
    void terminalStatesCannotTransition() {
        for (RequestStatus terminal : RequestStatus.TERMINAL) {
            for (RequestStatus target : RequestStatus.values()) {
                assertFalse(terminal.canTransitionTo(target),
                        terminal + " is terminal and must not move to " + target);
            }
        }
    }

    @Test
    void anyActiveStateCanBeCancelled() {
        assertTrue(RequestStatus.PENDING.canTransitionTo(RequestStatus.CANCELLED));
        assertTrue(RequestStatus.VERIFIED.canTransitionTo(RequestStatus.CANCELLED));
        assertTrue(RequestStatus.APPROVED.canTransitionTo(RequestStatus.CANCELLED));
        assertTrue(RequestStatus.FULFILLED.canTransitionTo(RequestStatus.CANCELLED));
    }
}
