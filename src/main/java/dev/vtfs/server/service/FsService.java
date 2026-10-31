package dev.vtfs.server.service;

import dev.vtfs.server.dto.DirectoryItem;
import dev.vtfs.server.dto.InodeInfo;
import dev.vtfs.server.entity.DirectoryEntry;
import dev.vtfs.server.entity.FsInode;
import dev.vtfs.server.entity.FsNodeType;
import dev.vtfs.server.exception.FsException;
import dev.vtfs.server.repository.DirectoryEntryRepository;
import dev.vtfs.server.repository.InodeRepository;
import dev.vtfs.server.storage.RedisFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FsService {
    private final InodeRepository inodeRepository;
    private final DirectoryEntryRepository entryRepository;
    private final RedisFileStorage redisFileStorage;
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

        if (target.getLinkCount() == 0) {
            inodeRepository.delete(target);
        }
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
        byte[] data = inode.getData();
        if (offset >= data.length) {
            return new byte[0];
        }

        int toRead = (int) Math.min(length, inode.getSize()-offset);
        return redisFileStorage.read(inodeId,offset,toRead);
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
        byte[] current = inode.getData();
        long position = append ? current.length : offset;
        if (position > MAX_FILE_SIZE || bytes.length > MAX_FILE_SIZE - position) {
            throw new FsException(27);
        }
        int start = (int) position;
        int newSize = Math.max(current.length, start + bytes.length);
        byte[] result = Arrays.copyOf(current,newSize);
        System.arraycopy(bytes,0,result,start,bytes.length);
        inode.setData(result);
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
        byte[] data = Arrays.copyOf(inode.getData(),(int) newSize);
        inode.setData(data);
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

    @Transactional
    public void rename(Long oldParentId, String oldName, Long newParentId, String newName) {
        validateName(oldName);
        validateName(newName);
        lockNamespace();
        FsInode oldParent = inodeRepository.findByIdForUpdate(oldParentId).orElseThrow(() -> new FsException(2));
        FsInode newParent = oldParentId.equals(newParentId) ? oldParent
                : inodeRepository.findByIdForUpdate(newParentId).orElseThrow(() -> new FsException(2));
        if (oldParent.getType() != FsNodeType.DIRECTORY || newParent.getType() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
        DirectoryEntry sourceEntry = entryRepository.findEntry(oldParentId,oldName).orElseThrow(() -> new FsException(2));

        if (oldParentId.equals(newParentId) && oldName.equals(newName)) {
            return;
        }

        FsInode source = inodeRepository.findByIdForUpdate(sourceEntry.getTarget().getId()).orElseThrow(() -> new FsException(2));

        if (source.getType() == FsNodeType.DIRECTORY && isSelfOrDescendant(source.getId(),newParentId)) {
            throw new FsException(22);
        }

        Optional<DirectoryEntry> existing = entryRepository.findEntry(newParentId,newName);
        if (existing.isPresent()) {
            DirectoryEntry destinationEntry = existing.get();

            FsInode destination = inodeRepository.findByIdForUpdate(
                    destinationEntry.getTarget().getId()).orElseThrow(() -> new FsException(2));
            if (source.getType() == FsNodeType.FILE && destination.getType() == FsNodeType.DIRECTORY) {
                throw new FsException(21);
            }
            if (source.getType() == FsNodeType.DIRECTORY && destination.getType() == FsNodeType.FILE) {
                throw new FsException(20);
            }
            if (destination.getType() == FsNodeType.DIRECTORY && entryRepository.countChildren(destination.getId()) > 0) {
                throw new FsException(39);
            }
            entryRepository.delete(destinationEntry);
            entryRepository.flush();

            if (destination.getType() == FsNodeType.FILE) {
                destination.decrementLinks();
                if (destination.getLinkCount() == 0) {
                    inodeRepository.delete(destination);
                }
            } else {
                inodeRepository.delete(destination);
                newParent.decrementLinks();
            }
        }
        sourceEntry.moveTo(newParent,newName);
        if (source.getType() == FsNodeType.DIRECTORY && !oldParent.equals(newParent)) {
            oldParent.decrementLinks();
            newParent.incrementLinks();
        }
        source.metadataChanged();
        oldParent.directoryContentsChanged();

        if (!oldParentId.equals(newParentId)) {
            newParent.directoryContentsChanged();
        }

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


    private void checkDirectory(Long inodeId) {
        InodeInfo inode = stat(inodeId);

        if (inode.type() != FsNodeType.DIRECTORY) {
            throw new FsException(20);
        }
    }
}
