package dev.vtfs.server.repository;

import dev.vtfs.server.dto.DirectoryItem;
import dev.vtfs.server.dto.InodeInfo;
import dev.vtfs.server.entity.DirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DirectoryEntryRepository extends JpaRepository<DirectoryEntry, Long> {

    @Query("""
        SELECT new dev.vtfs.server.dto.InodeInfo(e.target.id,e.target.type,e.target.permissions,e.target.size,e.target.linkCount)
        FROM DirectoryEntry e WHERE e.parent.id = :parentId AND e.name = :name
        """)
    Optional<InodeInfo> lookup(Long parentId, String name);

    @Query("""
        SELECT new dev.vtfs.server.dto.DirectoryItem(e.name,e.target.id,e.target.type) FROM DirectoryEntry e
        WHERE e.parent.id = :parentId ORDER BY e.name
        """)
    List<DirectoryItem> list(Long parentId);

    @Query("select e from DirectoryEntry e where e.parent.id = :parentId and e.name = :name")
    Optional<DirectoryEntry> findEntry(Long parentId, String name);

    @Query("select count(e) from DirectoryEntry e where e.parent.id = :parentId")
    long countChildren(Long parentId);

    @Query("select e.parent.id from DirectoryEntry e where e.target.id = :targetId")
    Optional<Long> findParentIdByTargetId(Long targetId);

}
