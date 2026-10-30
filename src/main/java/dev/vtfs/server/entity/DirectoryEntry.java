package dev.vtfs.server.entity;

import dev.vtfs.server.exception.FsException;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name="directory_entries", uniqueConstraints = @UniqueConstraint(name="uk_parent_name",
        columnNames = {"parent_inode_id","name"}))
public class DirectoryEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_inode_id", nullable = false)
    private FsInode parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_inode_id", nullable = false)
    private FsInode target;

    @Column(nullable = false, length = 255)
    private String name;

    public void moveTo(FsInode parent, String name) {
        if (parent.getType() != FsNodeType.DIRECTORY) {
            throw new IllegalArgumentException("Parent is not a directory");
        }
        this.parent = parent;
        this.name = name;
    }

    protected DirectoryEntry() {}

    public DirectoryEntry(FsInode parent, FsInode target, String name) {
        if (parent.getType() != FsNodeType.DIRECTORY) {
            throw new IllegalArgumentException("Parent is not a directory");
        }
        this.parent = parent;
        this.target = target;
        this.name = name;
    }

}
