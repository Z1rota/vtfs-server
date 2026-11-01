package dev.vtfs.server.service;

import dev.vtfs.server.entity.FsSession;
import dev.vtfs.server.repository.FsSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SessionCleanupScheduler {
    private final FsSessionRepository sessionRepository;
    private final FsService fsService;

    @Scheduled(fixedDelay = 30_000)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(90);

        for (FsSession session : sessionRepository.findByLastSeenAtBefore(cutoff)) {
            try {
                fsService.expireSession(session.getId(), cutoff);
            } catch (Exception e) {
                System.err.println("Failed to expire session " + session.getId());
                e.printStackTrace();
            }
        }
    }
}

