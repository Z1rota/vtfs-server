package dev.vtfs.server.entity;


import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "fs_sessions")
public class FsSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected FsSession() {}

    public FsSession(Instant now) {
        this.lastSeenAt = now;
    }

    public void heartbeat() {
        this.lastSeenAt = Instant.now();
    }
}
