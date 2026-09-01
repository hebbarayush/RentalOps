package com.rentalops.common.outbox;

import com.rentalops.auth.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only operational view of the outbox: queue depth, and manual "flush now" / "replay
 * failed" triggers. Useful in a demo to show the queue draining; in production it's the knob
 * you reach for when the poller is behind or something got parked as FAILED.
 */
@RestController
@RequestMapping("/api/admin/outbox")
public class OutboxController {
    private final OutboxProcessor processor;
    private final CurrentUserService currentUserService;

    public OutboxController(OutboxProcessor processor, CurrentUserService currentUserService) {
        this.processor = processor;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/stats")
    public OutboxProcessor.OutboxStats stats() {
        requireAdmin();
        return processor.stats();
    }

    @PostMapping("/process")
    public ProcessResult process() {
        requireAdmin();
        return new ProcessResult(processor.processPendingNow());
    }

    @PostMapping("/replay-failed")
    public ReplayResult replayFailed() {
        requireAdmin();
        return new ReplayResult(processor.replayFailed());
    }

    private void requireAdmin() {
        if (!currentUserService.isAdmin(currentUserService.requireCurrentUser())) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    public record ProcessResult(int delivered) {
    }

    public record ReplayResult(int requeued) {
    }
}
