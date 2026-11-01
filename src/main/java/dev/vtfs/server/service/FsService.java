package dev.vtfs.server.service;

import dev.vtfs.server.dto.DirectoryItem;
import dev.vtfs.server.dto.InodeInfo;
import dev.vtfs.server.entity.*;
import dev.vtfs.server.exception.FsException;
import dev.vtfs.server.repository.DirectoryEntryRepository;
import dev.vtfs.server.repository.FsOpenHandleRepository;
import dev.vtfs.server.repository.FsSessionRepository;
import dev.vtfs.server.repository.InodeRepository;
import dev.vtfs.server.storage.RedisFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FsService {
    private final InodeRepository inodeRepository;
    private final DirectoryEntryRepository entryRepository;
    private final RedisFileStorage redisFileStorage;
    private final FsSessionRepository sessionRepository;
    private final FsOpenHandleRepository fsOpenHandleRepository;
    private static final int MAX_READ_SIZE = 16 * 1024;
    private static final int MAX_WRITE_SIZE = 16 * 1024;
    private static final int MAX_FILE_SIZE = 1024 * 1024;

    public InodeInfo stat(Long inodeId) {
        return inodeRepository.findMetadataById(inodeId).orElseThrow(() -> new FsException(2));
    }

    public InodeInfo lookup(Long parentId, String name) {
        checkDirectory(parentId);
        return entryRepository.lookup(parentId,name).orElseThrow(() -> new FsException(2));
    }
    public List<DirectoryItem> list(Long parentId) {
        checkDirectory(parentId);
        return entryRepository.list(parentId);
    }
    @Transactional
    public InodeInfo create(Long parentId, String name) {
        return createNode(parentId,name,FsNodeType.FILE,0644);
    }
    @Transactional
    public InodeInfo mkdir(Long parentId, String name) {
        return createNode(parentId,name,FsNodeType.DIRECTORY,0755);
    }
    @Transactional
    public void unlink(Long parentId, String name) {
        validateName(name);
        lockNamespace();

        FsInode parent = inodeRepository.findByIdForUpdate(parentId).orElseThrow(() -> new FsException(2));

        if (parent.getType() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
        DirectoryEntry entry = entryRepository.findEntry(parentId,name).orElseThrow(() -> new FsException(2));

        FsInode target = inodeRepository.findByIdForUpdate(entry.getTarget().getId()).orElseThrow(() -> new FsException(2));

        if (target.getType() == FsNodeType.DIRECTORY) {
            throw new FsException(21);
        }
        entryRepository.delete(entry);
        entryRepository.flush();

        target.decrementLinks();
        deleteOrphanedInode(target);
        parent.directoryContentsChanged();
    }

    @Transactional
    public void rmdir(Long parentId, String name) {
        validateName(name);
        lockNamespace();

        FsInode parent = inodeRepository.findById(parentId).orElseThrow(() -> new FsException(2));

        if (parent.getType() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
        DirectoryEntry entry = entryRepository.findEntry(parentId,name).orElseThrow(() -> new FsException(2));

        FsInode target = inodeRepository.findByIdForUpdate(entry.getTarget().getId()).orElseThrow(() -> new FsException(2));
        if (target.getType() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
        if (target.isRoot()) {
            throw new FsException(16);
        }
        if (entryRepository.countChildren(target.getId()) > 0) {
            throw new FsException(39);
        }
        entryRepository.delete(entry);
        entryRepository.flush();
        inodeRepository.delete(target);
        parent.decrementLinks();
        parent.directoryContentsChanged();
    }

    public byte[] read(Long inodeId, long offset, int length) {
        if (offset < 0 || length < 0 || length > MAX_READ_SIZE) {
            throw new FsException(22);
        }
        FsInode inode = inodeRepository.findById(inodeId).orElseThrow(() -> new FsException(2));

        if (inode.getType() != FsNodeType.FILE) {
            throw new FsException(21);
        }
        if (offset >= inode.getSize() || length == 0) {
            return new byte[0];
        }

        int toRead = (int) Math.min(length, inode.getSize()-offset);
        byte[] res = redisFileStorage.read(inodeId,offset,toRead);
        if (res == null || res.length != toRead) {
            throw new FsException(5);
        }

        return res;
    }

    private InodeInfo createNode(Long parentId, String name, FsNodeType nodeType, int permissions) {
        validateName(name);
        lockNamespace();

        FsInode parent = inodeRepository.findByIdForUpdate(parentId).orElseThrow(() -> new FsException(2));
        if (parent.getType() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
        if (entryRepository.lookup(parentId,name).isPresent()) {
            throw new FsException(17);
        }
        FsInode inode = inodeRepository.save(new FsInode(nodeType,permissions,false));
        if (nodeType == FsNodeType.FILE) {
            redisFileStorage.create(inode.getId());
        }

        entryRepository.save(new DirectoryEntry(parent,inode,name));

        if (nodeType == FsNodeType.DIRECTORY) {
            parent.incrementLinks();
        }
        parent.directoryContentsChanged();
        return new InodeInfo(inode.getId(),inode.getType(), inode.getPermissions(), inode.getSize(), inode.getLinkCount());
    }

    @Transactional
    public int write(Long inodeId, long offset, byte[] bytes, boolean append) {
        if (offset < 0 || bytes.length > MAX_WRITE_SIZE) {
            throw new FsException(22);
        }
        FsInode inode = inodeRepository.findByIdForUpdate(inodeId).orElseThrow(() -> new FsException(2));
        if (inode.getType() != FsNodeType.FILE) {
            throw new FsException(21);
        }
        if (bytes.length == 0) {
            return 0;
        }
        long position = append ? inode.getSize() : offset;
        if (position > MAX_FILE_SIZE || bytes.length > MAX_FILE_SIZE - position) {
            throw new FsException(27);
        }
        redisFileStorage.write(inodeId,position,bytes);
        long newSize = Math.max(inode.getSize(),position + bytes.length);
        inode.updateSize(newSize);
        return bytes.length;
    }

    @Transactional
    public void truncate(Long inodeId, long newSize) {
        if (newSize < 0 || newSize > MAX_FILE_SIZE) {
            throw new FsException(22);
        }
        FsInode inode = inodeRepository.findByIdForUpdate(inodeId).orElseThrow(() -> new FsException(2));
        if (inode.getType() != FsNodeType.FILE) {
            throw new FsException(21);
        }
        if (inode.getSize() == newSize) {
            return;
        }
        redisFileStorage.truncate(inodeId,newSize);
        inode.updateSize(newSize);
    }

    @Transactional
    public InodeInfo link(Long inodeId, Long parentId, String name) {
        validateName(name);
        lockNamespace();

        FsInode parent = inodeRepository.findByIdForUpdate(parentId).orElseThrow(() -> new FsException(2));

        if (parent.getType() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
        FsInode target = inodeRepository.findByIdForUpdate(inodeId).orElseThrow(() -> new FsException(2));
        if (target.getType() != FsNodeType.FILE) {
            throw new FsException(1);
        }
        if (target.getLinkCount() == 0) {
            throw new FsException(2);
        }
        if (entryRepository.findEntry(parentId,name).isPresent()) {
            throw new FsException(17);
        }
        entryRepository.save(new DirectoryEntry(parent,target,name));
        target.incrementLinks();
        parent.directoryContentsChanged();
        return new InodeInfo(target.getId(),target.getType(), target.getPermissions(), target.getSize(),target.getLinkCount());
    }

    private boolean isSelfOrDescendant(Long directoryId, Long parentId) {
        Long current = parentId;

        while (current != null) {
            if (current.equals(directoryId)) {
                return true;
            }
            current = entryRepository.findParentIdByTargetId(current).orElse(null);
        }
        return false;
    }

    public InodeInfo root() {
        FsInode root = inodeRepository.findRoot().orElseThrow(() -> new FsException(5));

        return new InodeInfo(root.getId(), root.getType(), root.getPermissions(), root.getSize(), root.getLinkCount());
    }

    private void lockNamespace() {
        inodeRepository.findRootForUpdate().orElseThrow(() -> new FsException(5));
    }

    private void validateName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") ||
                name.indexOf('\0') >= 0 || name.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new FsException(22);
        }
    }

    @Transactional
    public Long startSession() {
        FsSession session = sessionRepository.save(new FsSession(Instant.now()));
        return session.getId();
    }
    @Transactional
    public void heartbeat(Long sessionId) {
        FsSession session = sessionRepository.findByIdForUpdate(sessionId).orElseThrow(() -> new FsException(2));
        session.heartbeat();
    }

    @Transactional
    public Long open(Long sessionId, Long inodeId) {
        FsSession session = sessionRepository.findByIdForUpdate(sessionId).orElseThrow(() -> new FsException(2));

        FsInode inode = inodeRepository.findByIdForUpdate(inodeId).orElseThrow(() -> new FsException(2));

        if (inode.getType() != FsNodeType.FILE) {
            throw new FsException(21);
        }
        FsOpenHandle handle = fsOpenHandleRepository.save(new FsOpenHandle(sessionId, inodeId));
        session.heartbeat();
        return handle.getId();
    }

    @Transactional
    public void closeHandle(Long sessionId, Long handleId) {
        FsSession session = sessionRepository.findByIdForUpdate(sessionId).orElseThrow(() -> new FsException(2));

        FsOpenHandle handle = fsOpenHandleRepository.findByIdAndSessionId(handleId, sessionId)
                .orElseThrow(() -> new FsException(9));

        FsInode inode = inodeRepository.findByIdForUpdate(handle.getInodeId()).orElseThrow(() -> new FsException(2));

        fsOpenHandleRepository.delete(handle);
        fsOpenHandleRepository.flush();

        deleteOrphanedInode(inode);
        session.heartbeat();
    }

    @Transactional
    public void endSession(Long sessionId) {
        FsSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);

        if (session == null) {
            return;
        }
        finishSession(session);
    }

    @Transactional
    public void expireSession(Long sessionId, Instant cutoff) {
        FsSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);

        if (session == null || !session.getLastSeenAt().isBefore(cutoff)) {
            return;
        }

        finishSession(session);
    }

    private void checkDirectory(Long inodeId) {
        InodeInfo inode = stat(inodeId);

        if (inode.type() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
    }

    private void deleteOrphanedInode(FsInode inode) {
        if (inode.getLinkCount() == 0 && fsOpenHandleRepository.countByInodeId(inode.getId()) == 0) {

            Long inodeId = inode.getId();

            inodeRepository.delete(inode);
            deleteContentAfterCommit(inodeId);
        }
    }

    private void deleteContentAfterCommit(Long inodeId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            redisFileStorage.delete(inodeId);
                        } catch (Exception e) {
                            System.err.println("Failed to delete Redis content for inode " + inodeId);
                        }
                    }
                }
        );
    }

    private void finishSession(FsSession session) {
        List<FsOpenHandle> handles = fsOpenHandleRepository.findBySessionId(session.getId());

        for (FsOpenHandle handle : handles) {
            FsInode inode = inodeRepository.findByIdForUpdate(handle.getInodeId()).orElse(null);

            fsOpenHandleRepository.delete(handle);
            fsOpenHandleRepository.flush();

            if (inode != null) {
                deleteOrphanedInode(inode);
            }
        }
        sessionRepository.delete(session);
    }
}
