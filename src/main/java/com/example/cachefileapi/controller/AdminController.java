package com.example.cachefileapi.controller;

import com.example.cachefileapi.service.FileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Testing/ops utility endpoints — not intended for production use without proper auth.
 *
 * <p>Base path: {@code /api/admin} (context path {@code /api} is set in
 * {@code application.yml}, this controller maps {@code /admin}).</p>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final FileCleanupService fileCleanupService;

    /**
     * {@code POST /admin/cleanup} — manually trigger orphaned file cleanup on demand.
     *
     * <p>Testing/ops utility: invokes the same logic as the scheduled cleanup job
     * without waiting for the cron trigger.</p>
     *
     * @return 200 OK with {@code {"removedCount": N}}
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Integer>> triggerCleanup() {
        log.info("Manual cleanup triggered via POST /admin/cleanup");
        int removed = fileCleanupService.cleanupOrphanedFiles();
        return ResponseEntity.ok(Map.of("removedCount", removed));
    }
}
