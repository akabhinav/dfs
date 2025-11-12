package com.dfs.common;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Information about a DataNode in the cluster.
 */
public class DataNodeInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final String host;
    private final int port;
    private long availableSpace;
    private long usedSpace;
    private long totalSpace;
    private long lastHeartbeat;
    private boolean active;

    public DataNodeInfo(String host, int port, long totalSpace) {
        this.nodeId = UUID.randomUUID().toString();
        this.host = host;
        this.port = port;
        this.totalSpace = totalSpace;
        this.availableSpace = totalSpace;
        this.usedSpace = 0;
        this.lastHeartbeat = System.currentTimeMillis();
        this.active = true;
    }

    public DataNodeInfo(String nodeId, String host, int port, long totalSpace) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.totalSpace = totalSpace;
        this.availableSpace = totalSpace;
        this.usedSpace = 0;
        this.lastHeartbeat = System.currentTimeMillis();
        this.active = true;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getAvailableSpace() {
        return availableSpace;
    }

    public void setAvailableSpace(long availableSpace) {
        this.availableSpace = availableSpace;
    }

    public long getUsedSpace() {
        return usedSpace;
    }

    public void setUsedSpace(long usedSpace) {
        this.usedSpace = usedSpace;
    }

    public long getTotalSpace() {
        return totalSpace;
    }

    public void setTotalSpace(long totalSpace) {
        this.totalSpace = totalSpace;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataNodeInfo that = (DataNodeInfo) o;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public String toString() {
        return "DataNodeInfo{" +
                "nodeId='" + nodeId + '\'' +
                ", address='" + getAddress() + '\'' +
                ", availableSpace=" + availableSpace +
                ", usedSpace=" + usedSpace +
                ", active=" + active +
                '}';
    }
}
