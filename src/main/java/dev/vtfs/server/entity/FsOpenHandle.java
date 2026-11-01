package dev.vtfs.server.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "fs_open_handles")
public class FsOpenHandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="session_id",nullable = false)
    private Long SessionId;

    @Column(name="inode_id",nullable = false)
    private Long inodeId;

    protected FsOpenHandle() {}
    public FsOpenHandle(Long SessionId, Long inodeId) {
        this.SessionId = SessionId;
        this.inodeId = inodeId;
    }
}
