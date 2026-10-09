package com.sben.notification.email;

import com.sben.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Email delivery log for administrators: what was sent to whom, what is still
 * queued, what failed and why, with a retry for failures. Bodies are not
 * listed; one-time codes are never shown (they are wiped after delivery).
 */
@RestController
@RequestMapping("/api/v1/admin/emails")
@RequiredArgsConstructor
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class EmailAdminController {

    private final EmailOutboxRepository repository;
    private final EmailDispatcher dispatcher;
    private final EmailPreviewService previewService;

    public record EmailRow(Long id, Long userId, String recipient, EmailCategory category, String template,
                           String subject, EmailStatus status, int attempts, String lastError,
                           LocalDateTime createdAt, LocalDateTime sentAt, LocalDateTime nextAttemptAt) {
        static EmailRow from(EmailOutbox e) {
            return new EmailRow(e.getId(), e.getUserId(), e.getRecipient(), e.getCategory(), e.getTemplate(),
                    e.getSubject(), e.getStatus(), e.getAttempts(), e.getLastError(), e.getCreatedAt(),
                    e.getSentAt(), e.getStatus() == EmailStatus.PENDING ? e.getNextAttemptAt() : null);
        }
    }

    public record Summary(long pending, long sending, long sent, long failed, long suppressed) {}

    @GetMapping
    public ApiResponse<Page<EmailRow>> list(@RequestParam(required = false) EmailStatus status,
                                            @RequestParam(required = false) EmailCategory category,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return ApiResponse.ok(repository.search(status, category, like, PageRequest.of(Math.max(page, 0), 25))
                .map(EmailRow::from));
    }

    @GetMapping("/summary")
    public ApiResponse<Summary> summary() {
        return ApiResponse.ok(new Summary(
                repository.countByStatus(EmailStatus.PENDING), repository.countByStatus(EmailStatus.SENDING),
                repository.countByStatus(EmailStatus.SENT), repository.countByStatus(EmailStatus.FAILED),
                repository.countByStatus(EmailStatus.SUPPRESSED)));
    }

    public record PreviewRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String to) {}

    /** Send one sample of every template to an administrator's inbox (subjects start with "[Preview]"). */
    @PostMapping("/previews")
    public ApiResponse<Integer> sendPreviews(@jakarta.validation.Valid @RequestBody PreviewRequest req) {
        int n = previewService.sendAll(req.to());
        return ApiResponse.ok(n + " preview emails queued for " + req.to().trim(), n);
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable Long id) {
        dispatcher.retry(id);
        return ApiResponse.ok("Email queued for another attempt", null);
    }
}
