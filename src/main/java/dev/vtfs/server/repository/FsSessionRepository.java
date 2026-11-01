package dev.vtfs.server.repository;

import dev.vtfs.server.entity.FsSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FsSessionRepository extends JpaRepository<FsSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FsSession s where s.id = :id")
    Optional<FsSession> findByIdForUpdate(Long id);

    List<FsSession> findByLastSeenAtBefore(Instant lastSeenAt);
}
