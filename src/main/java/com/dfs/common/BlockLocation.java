package com.dfs.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the location of block replicas across DataNodes.
 */
public class BlockLocation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String blockId;
    private final List<String> dataNodeIds;

    public BlockLocation(String blockId) {
        this.blockId = blockId;
        this.dataNodeIds = new ArrayList<>();
    }

    public BlockLocation(String blockId, List<String> dataNodeIds) {
        this.blockId = blockId;
        this.dataNodeIds = new ArrayList<>(dataNodeIds);
    }

    public String getBlockId() {
        return blockId;
    }

    public List<String> getDataNodeIds() {
        return new ArrayList<>(dataNodeIds);
    }

    public void addDataNode(String nodeId) {
        if (!dataNodeIds.contains(nodeId)) {
            dataNodeIds.add(nodeId);
        }
    }

    public void removeDataNode(String nodeId) {
        dataNodeIds.remove(nodeId);
    }

    public int getReplicationCount() {
        return dataNodeIds.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockLocation that = (BlockLocation) o;
        return Objects.equals(blockId, that.blockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId);
    }

    @Override
    public String toString() {
        return "BlockLocation{" +
                "blockId='" + blockId + '\'' +
                ", replicationCount=" + dataNodeIds.size() +
                ", dataNodeIds=" + dataNodeIds +
                '}';
    }
}
