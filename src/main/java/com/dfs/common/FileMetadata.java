package com.dfs.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metadata for a file in the distributed file system.
 */
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final String filePath;
    private final long fileSize;
    private final List<String> blockIds;
    private final long creationTime;
    private final long modificationTime;
    private final int replicationFactor;

    public FileMetadata(String fileName, String filePath, long fileSize,
                       List<String> blockIds, int replicationFactor) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.blockIds = new ArrayList<>(blockIds);
        this.creationTime = System.currentTimeMillis();
        this.modificationTime = creationTime;
        this.replicationFactor = replicationFactor;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public List<String> getBlockIds() {
        return new ArrayList<>(blockIds);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getModificationTime() {
        return modificationTime;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", blockCount=" + blockIds.size() +
                ", replicationFactor=" + replicationFactor +
                '}';
    }
}
