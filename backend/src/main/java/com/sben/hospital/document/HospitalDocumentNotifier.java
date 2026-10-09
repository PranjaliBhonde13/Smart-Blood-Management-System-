package com.sben.hospital.document;

import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.RequestStatus;
import com.sben.hospital.Hospital;
import com.sben.hospital.document.dto.HospitalDocumentViews.Compliance;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/** In-app + email notifications for hospital document events. */
@Component
@RequiredArgsConstructor
public class HospitalDocumentNotifier {

    /** Requests a hospital may still have to act on. */
    static final EnumSet<RequestStatus> IN_PROGRESS =
            EnumSet.of(RequestStatus.VERIFIED, RequestStatus.APPROVED, RequestStatus.FULFILLED);

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BloodRequestRepository requestRepository;

    public void hospital(Hospital hospital, String title, String message) {
        notificationService.notify(hospital.getUserId(), title, message, Notification.Type.VERIFICATION);
    }

    public void admins(String title, String message) {
        for (User admin : userRepository.findByRoleAndDeletedFalse(Role.ADMIN, Pageable.unpaged())) {
            if (!admin.isEnabled()) continue;
            notificationService.notify(admin.getId(), title, message, Notification.Type.VERIFICATION);
        }
    }

    public long activeRequests(Hospital hospital) {
        return requestRepository.countByHospitalIdAndStatusInAndDeletedFalse(hospital.getId(), IN_PROGRESS);
    }

    /**
     * A previously fully verified hospital just lost that status. Admins are told
     * how many in-progress requests it owns: those requests keep their current
     * state (existing rules) but the hospital cannot move them on until it is
     * verified again, so an admin may need to step in.
     */
    public void verificationLost(Hospital hospital, Compliance after, String cause) {
        long active = activeRequests(hospital);
        admins("Hospital no longer fully verified",
                hospital.getHospitalName() + " lost its verified status (" + cause + "). "
                        + (active > 0 ? active + " in-progress request(s) owned by this hospital are paused until it is verified again. " : "")
                        + "Review it under Verifications.");
        hospital(hospital, "Verification on hold",
                "Your hospital is no longer fully verified: " + after.message()
                        + " Restricted request actions are disabled until this is resolved.");
    }
}
