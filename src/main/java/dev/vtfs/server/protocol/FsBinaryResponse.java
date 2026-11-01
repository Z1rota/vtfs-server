package dev.vtfs.server.protocol;

import dev.vtfs.server.dto.DirectoryItem;
import dev.vtfs.server.dto.InodeInfo;
import dev.vtfs.server.entity.FsNodeType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FsBinaryResponse {
    public static byte[] inode(InodeInfo inode) {
        ByteBuffer buffer = ByteBuffer.allocate(33).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0);
        buffer.putLong(inode.id());
        buffer.put(nodeType(inode.type()));
        buffer.putInt(inode.permissions());
        buffer.putLong(inode.size());
        buffer.putInt(inode.linkCount());

        return buffer.array();
    }

    public static byte[] list(List<DirectoryItem> items) {
        List<byte[]> names = new ArrayList<>();
        int size = Long.BYTES + Integer.BYTES;

        for (DirectoryItem item : items) {
            byte[] name = item.name().getBytes(StandardCharsets.UTF_8);
            names.add(name);
            size += Short.BYTES + name.length + Long.BYTES + Byte.BYTES;
        }

        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0);
        buffer.putInt(items.size());

        for (int i = 0; i < items.size(); i++) {
            DirectoryItem item = items.get(i);
            byte[] name = names.get(i);

            buffer.putShort((short) name.length);
            buffer.put(name);
            buffer.putLong(item.inodeId());
            buffer.put(nodeType(item.type()));
        }

        return buffer.array();
    }

    public static byte[] read(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + data.length).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putLong(0);
        buffer.put(data);

        return buffer.array();
    }

    public static byte[] write(int written) {
        return ByteBuffer.allocate(Long.BYTES + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(0)
                .putInt(written)
                .array();
    }


    public static byte[] status(int errno) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(errno).array();
    }

    private static byte nodeType(FsNodeType type) {
        return (byte) (type == FsNodeType.DIRECTORY ? 2 : 1);
    }

    public static byte[] identifier(Long id) {
        return ByteBuffer.allocate(16)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(0)
                .putLong(id)
                .array();
    }
}
