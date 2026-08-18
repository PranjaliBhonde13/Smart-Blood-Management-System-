package com.sben.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndStatus(Long userId, Notification.Status status);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = com.sben.notification.Notification.Status.READ " +
           "WHERE n.userId = :userId AND n.status = com.sben.notification.Notification.Status.UNREAD")
    void markAllRead(Long userId);
}
