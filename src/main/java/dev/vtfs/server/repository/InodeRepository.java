package dev.vtfs.server.repository;

import dev.vtfs.server.dto.InodeInfo;
import dev.vtfs.server.entity.FsInode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InodeRepository extends JpaRepository<FsInode,Long> {

    @Query("SELECT i FROM FsInode i WHERE i.root = true")
    Optional<FsInode> findRoot();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM FsInode i WHERE i.id = :id")
    Optional<FsInode> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT new dev.vtfs.server.dto.InodeInfo(i.id, i.type, i.permissions, i.size,i.linkCount) " +
            "FROM FsInode i WHERE i.id = :id ")
    Optional<InodeInfo> findMetadataById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FsInode i where i.root = true")
    Optional<FsInode> findRootForUpdate();
}
