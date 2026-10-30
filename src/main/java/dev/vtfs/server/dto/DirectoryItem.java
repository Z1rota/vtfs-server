package dev.vtfs.server.dto;

import dev.vtfs.server.entity.FsNodeType;

public record DirectoryItem(String name, Long inodeId, FsNodeType type) { }
