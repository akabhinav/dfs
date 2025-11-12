package com.dfs.common;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a data block in the distributed file system.
 * Each file is split into multiple blocks of fixed size.
 */
public class Block implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String blockId;
    private final long blockSize;
    private final long timestamp;
    private final String checksum;

    public Block(String blockId, long blockSize, String checksum) {
        this.blockId = blockId;
        this.blockSize = blockSize;
        this.timestamp = System.currentTimeMillis();
        this.checksum = checksum;
    }

    public static Block create(long blockSize, String checksum) {
        return new Block(UUID.randomUUID().toString(), blockSize, checksum);
    }

    public String getBlockId() {
        return blockId;
    }

    public long getBlockSize() {
        return blockSize;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return Objects.equals(blockId, block.blockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId);
    }

    @Override
    public String toString() {
        return "Block{" +
                "blockId='" + blockId + '\'' +
                ", blockSize=" + blockSize +
                ", timestamp=" + timestamp +
                ", checksum='" + checksum + '\'' +
                '}';
    }
}
