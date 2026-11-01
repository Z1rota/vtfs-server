package dev.vtfs.server.repository;

import dev.vtfs.server.entity.FsOpenHandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FsOpenHandleRepository extends JpaRepository<FsOpenHandle, Long> {
    long countByInodeId(long inodeId);
    List<FsOpenHandle> findBySessionId(long sessionId);
    Optional<FsOpenHandle> findByIdAndSessionId(long id, long sessionId);

}
