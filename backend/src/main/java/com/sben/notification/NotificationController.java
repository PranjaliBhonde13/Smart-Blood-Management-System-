package com.sben.notification;

import com.sben.common.response.ApiResponse;
import com.sben.notification.dto.NotificationResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** In-app notification endpoints (see docs/04-API.md §8). Any authenticated user. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(@AuthenticationPrincipal UserPrincipal me,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.list(me.getId(), pageable).map(NotificationResponse::from));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(Map.of("count", service.unreadCount(me.getId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me) {
        service.markRead(id, me.getId());
        return ApiResponse.ok("Marked as read", null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal UserPrincipal me) {
        service.markAllRead(me.getId());
        return ApiResponse.ok("All marked as read", null);
    }
}
