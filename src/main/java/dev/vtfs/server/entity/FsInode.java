package dev.vtfs.server.entity;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.Version;

import java.time.Instant;

@Entity
@Getter
@Table(name="fs_inodes")
public class FsInode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FsNodeType type;

    @Column(nullable = false)
    private int permissions;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private int linkCount;

    @Column(nullable = false)
    private boolean root;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] data;

    @Column(nullable = false)
    private Instant modifiedAt;

    @Column(nullable = false)
    private Instant changedAt;

    @Version
    private Long version;

    protected FsInode() {}

    public FsInode(FsNodeType type, int permissions, boolean root) {
        this.type = type;
        this.permissions = permissions;
        this.root = root;
        this.size = 0;
        this.linkCount = type == FsNodeType.DIRECTORY ? 2 : 1;
        this.data = type == FsNodeType.FILE ? new byte[0] : null;

        Instant now = Instant.now();
        this.modifiedAt = now;
        this.changedAt = now;
    }

    public void incrementLinks() {
        linkCount++;
        changedAt = Instant.now();
    }
    public void decrementLinks() {
        if (linkCount <= 0) {
            throw new IllegalStateException("Invalid link count");
        }
        linkCount--;
        changedAt = Instant.now();
    }

    public void directoryContentsChanged() {
        if (type != FsNodeType.DIRECTORY) {
            throw new IllegalStateException("Invalid type");
        }
        Instant now = Instant.now();
        modifiedAt = now;
        changedAt = now;
    }

    public void metadataChanged() {
        changedAt = Instant.now();
    }

    public void setData(byte[] data) {
        if (this.type != FsNodeType.FILE) {
            throw new IllegalStateException("u can write only in files");
        }
        this.data = data.clone();
        this.size = data.length;
        this.modifiedAt = Instant.now();
        this.changedAt = this.modifiedAt;
    }



}
