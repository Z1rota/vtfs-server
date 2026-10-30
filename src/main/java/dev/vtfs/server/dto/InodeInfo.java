package dev.vtfs.server.dto;

import dev.vtfs.server.entity.FsNodeType;

public record InodeInfo(Long id, FsNodeType type, int permissions, long size,int linkCount) {
}
