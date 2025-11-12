# Architecture Documentation

## Overview

The Distributed File Storage System (DFS) is designed using a master-worker architecture, similar to Apache Hadoop HDFS. It provides reliable, scalable storage for massive datasets.

## System Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         DFS Cluster                               │
│                                                                   │
│  ┌────────────┐         ┌────────────┐       ┌────────────┐     │
│  │  Client 1  │         │  Client 2  │       │  Client N  │     │
│  └─────┬──────┘         └─────┬──────┘       └─────┬──────┘     │
│        │                      │                     │             │
│        └──────────────────────┼─────────────────────┘             │
│                               │                                   │
│                    ┌──────────▼───────────┐                       │
│                    │     NameNode         │                       │
│                    │  (Metadata Master)   │                       │
│                    │                      │                       │
│                    │ • File Namespace     │                       │
│                    │ • Block Mapping      │                       │
│                    │ • DataNode Registry  │                       │
│                    └──────────┬───────────┘                       │
│                               │                                   │
│              ┌────────────────┼────────────────┐                  │
│              │                │                │                  │
│         ┌────▼─────┐    ┌────▼─────┐    ┌────▼─────┐            │
│         │DataNode 1│    │DataNode 2│    │DataNode N│            │
│         │          │    │          │    │          │            │
│         │ Blocks:  │    │ Blocks:  │    │ Blocks:  │            │
│         │ B1, B2   │    │ B1, B3   │    │ B2, B3   │            │
│         └──────────┘    └──────────┘    └──────────┘            │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. NameNode (Master)

**Responsibilities:**
- Maintain file system namespace (directory tree)
- Track file-to-block mapping
- Track block-to-DataNode mapping
- Handle client metadata requests
- Monitor DataNode health
- Manage block replication

**Data Structures:**
```java
Map<String, FileMetadata> fileMetadataMap     // filePath -> metadata
Map<String, Block> blockMap                   // blockId -> block info
Map<String, BlockLocation> blockLocationMap   // blockId -> DataNode locations
Map<String, DataNodeInfo> dataNodeMap         // nodeId -> DataNode info
```

**Key Operations:**
- `handleWriteRequest()`: Allocate blocks, select DataNodes
- `handleReadRequest()`: Return block locations
- `handleDeleteRequest()`: Mark blocks for deletion
- `handleHeartbeat()`: Update DataNode status
- `handleBlockReport()`: Sync block locations

**Thread Model:**
- Main thread: Accept incoming connections
- Request executor pool: Handle client requests (10 threads)
- Heartbeat monitor: Check DataNode health (1 scheduled thread)

### 2. DataNode (Worker)

**Responsibilities:**
- Store data blocks on local disk
- Serve block data to clients
- Send heartbeats to NameNode
- Report block inventory to NameNode
- Execute replication commands

**Storage Model:**
```
data-directory/
├── datanode-9001/
│   ├── block-uuid-1.block
│   ├── block-uuid-2.block
│   └── ...
├── datanode-9002/
│   └── ...
```

**Key Operations:**
- `handleWriteBlock()`: Receive and store block data
- `handleReadBlock()`: Read and send block data
- `handleDeleteBlock()`: Delete block from disk
- `sendHeartbeat()`: Periodic status update to NameNode
- `sendBlockReport()`: Send list of all stored blocks

**Thread Model:**
- Main thread: Accept client connections
- Request executor pool: Handle block operations (10 threads)
- Heartbeat executor: Send periodic heartbeats (1 scheduled thread)

### 3. Client

**Responsibilities:**
- Provide user-facing API
- Contact NameNode for metadata
- Directly read/write blocks from/to DataNodes
- Handle retries and failover

**Key Operations:**
- `writeFile()`: Split file, get block assignments, write to DataNodes
- `readFile()`: Get block locations, read from DataNodes, assemble file
- `deleteFile()`: Request NameNode to delete file
- `listFiles()`: Get file listing from NameNode

## Data Flow

### Write Operation

```
Client                NameNode              DataNode1  DataNode2  DataNode3
  │                      │                      │         │         │
  │──Write Request──────►│                      │         │         │
  │   (file info)        │                      │         │         │
  │                      │                      │         │         │
  │◄─Block Assignment────│                      │         │         │
  │   (blocks, DNs)      │                      │         │         │
  │                      │                      │         │         │
  │──Block 1─────────────┼─────────────────────►│         │         │
  │                      │                      │         │         │
  │                      │                      │──Replicate Block──►│
  │                      │                      │         │         │
  │◄─ACK─────────────────┼──────────────────────│         │         │
  │                      │                      │         │         │
  │──Block 2─────────────┼──────────────────────┼────────►│         │
  │                      │                      │         │         │
  │                      │                      │         │──Replicate──►│
  │                      │                      │         │         │
  │◄─ACK─────────────────┼──────────────────────┼─────────│         │
  │                      │                      │         │         │
```

**Steps:**
1. Client sends write request to NameNode with file path and size
2. NameNode calculates number of blocks needed
3. NameNode selects DataNodes for each block (based on available space)
4. NameNode returns block IDs and DataNode assignments
5. Client splits file into blocks
6. Client writes each block to assigned DataNodes
7. DataNodes replicate blocks to other DataNodes
8. DataNodes send acknowledgments to Client

### Read Operation

```
Client                NameNode              DataNode1  DataNode2
  │                      │                      │         │
  │──Read Request───────►│                      │         │
  │   (file path)        │                      │         │
  │                      │                      │         │
  │◄─Block Locations─────│                      │         │
  │   (blocks, DNs)      │                      │         │
  │                      │                      │         │
  │──Read Block 1────────┼─────────────────────►│         │
  │                      │                      │         │
  │◄─Block Data──────────┼──────────────────────│         │
  │                      │                      │         │
  │──Read Block 2────────┼──────────────────────┼────────►│
  │                      │                      │         │
  │◄─Block Data──────────┼──────────────────────┼─────────│
  │                      │                      │         │
  │──Assemble File───────│                      │         │
  │                      │                      │         │
```

**Steps:**
1. Client sends read request to NameNode with file path
2. NameNode looks up file metadata
3. NameNode returns block IDs and DataNode locations
4. Client reads each block from a DataNode (tries replicas on failure)
5. Client assembles blocks into complete file
6. Client verifies checksums for data integrity

## Fault Tolerance

### DataNode Failure Detection

```java
// Heartbeat mechanism
heartbeatMonitor.scheduleAtFixedRate(() -> {
    long currentTime = System.currentTimeMillis();
    for (DataNodeInfo node : dataNodeMap.values()) {
        if (currentTime - node.getLastHeartbeat() > timeout) {
            node.setActive(false); // Mark as inactive
        }
    }
}, timeout, timeout, TimeUnit.MILLISECONDS);
```

**Process:**
1. DataNodes send heartbeats every 3 seconds (configurable)
2. NameNode monitors last heartbeat timestamp
3. If no heartbeat for 10 seconds (configurable), mark DataNode as inactive
4. Clients automatically failover to replica blocks on other DataNodes

### Data Integrity

**Checksums:**
- Calculated when writing blocks
- Verified when reading blocks
- Simple additive checksum (production should use CRC32 or MD5)

```java
public static String calculateChecksum(byte[] data) {
    long checksum = 0;
    for (byte b : data) {
        checksum += (b & 0xFF);
    }
    return String.format("%016x", checksum);
}
```

### Replication

**Replication Factor: 3 (default)**
- Each block is stored on 3 different DataNodes
- Provides fault tolerance for 2 DataNode failures
- Configurable based on requirements

**Replica Placement:**
- Currently: Simple selection by available space
- Production Enhancement: Rack-aware placement for better fault tolerance

## Scalability

### Handling Petabyte Scale

**Block Size Optimization:**
- Default: 128 MB blocks
- Large blocks reduce metadata overhead
- Example: 1 PB = ~8 million blocks (manageable in memory)

**NameNode Memory Requirements:**
```
Per file: ~150 bytes (FileMetadata)
Per block: ~200 bytes (Block + BlockLocation)

For 1 PB of data:
- ~8 million blocks
- ~1.6 GB RAM for block metadata
- Additional RAM for file metadata
- Total: ~2-4 GB RAM per PB
```

**Horizontal Scaling:**
- Add more DataNodes to increase storage capacity
- DataNodes can be commodity hardware
- No limit on number of DataNodes

**Vertical Scaling (NameNode):**
- Increase RAM for more files/blocks
- Single NameNode design (can be enhanced with federation)

### Performance Characteristics

**Write Throughput:**
- Limited by network bandwidth and disk I/O
- Parallel writes to multiple DataNodes
- Throughput = min(network_bandwidth, disk_write_speed)

**Read Throughput:**
- Can read different blocks from different DataNodes in parallel
- Throughput = sum(DataNode_bandwidths) for parallel reads

**Latency:**
- Write latency: Network RTT + disk write time + replication time
- Read latency: Network RTT + disk read time

## Network Protocol

### Message Types

```java
enum MessageType {
    // Client ↔ NameNode
    WRITE_REQUEST, WRITE_RESPONSE,
    READ_REQUEST, READ_RESPONSE,
    DELETE_REQUEST, DELETE_RESPONSE,
    LIST_REQUEST, LIST_RESPONSE,

    // DataNode ↔ NameNode
    REGISTER_DATANODE, REGISTER_ACK,
    HEARTBEAT, BLOCK_REPORT,
    REPLICATE_BLOCK, DELETE_BLOCK,

    // Client ↔ DataNode
    WRITE_BLOCK, READ_BLOCK,
    BLOCK_DATA, BLOCK_ACK,

    // Generic
    SUCCESS, ERROR
}
```

### Communication Pattern

**Synchronous RPC-style:**
- Request-response pattern
- TCP socket connections
- Java serialization for messages
- Binary data for block transfers

## Configuration

### Key Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| dfs.block.size | 128 MB | Size of each block |
| dfs.replication.factor | 3 | Number of replicas |
| dfs.namenode.port | 9000 | NameNode listen port |
| dfs.datanode.port | 9001 | DataNode base port |
| dfs.heartbeat.interval | 3000 ms | Heartbeat frequency |
| dfs.heartbeat.timeout | 10000 ms | Failure detection timeout |
| dfs.data.dir | ./dfs-data | Data storage directory |

## Production Enhancements

### Current Limitations

1. **NameNode Single Point of Failure**
   - Enhancement: Add Secondary NameNode or HA setup
   - Enhancement: Periodic metadata checkpointing

2. **In-Memory Metadata**
   - Enhancement: Persist metadata to disk
   - Enhancement: Use RocksDB or similar for large namespaces

3. **No Authentication**
   - Enhancement: Add Kerberos authentication
   - Enhancement: Implement ACLs for file permissions

4. **Simple Replica Placement**
   - Enhancement: Rack-aware placement
   - Enhancement: Consider DataNode load and disk usage

5. **No Erasure Coding**
   - Enhancement: Add Reed-Solomon erasure coding
   - Enhancement: Reduce storage overhead from 3x to 1.5x

### Recommended Production Setup

**Hardware:**
- NameNode: 64+ GB RAM, SSD for metadata, high-speed network
- DataNodes: 12+ HDDs, 32+ GB RAM, 10 Gbps network

**Software:**
- Enable persistent metadata storage
- Implement Secondary NameNode
- Add monitoring and alerting
- Implement security features

**Deployment:**
- Use dedicated NameNode machines
- Distribute DataNodes across multiple racks
- Implement load balancing for clients

## Testing Strategy

### Unit Tests
- Test individual components (Block, DataNodeInfo, etc.)
- Test utility functions (checksum, network utils)
- Test configuration management

### Integration Tests
- Test full write-read cycle
- Test replication
- Test fault tolerance
- Test concurrent operations
- Test large file handling

### Load Tests (Recommended)
- Test with thousands of files
- Test with multi-GB files
- Test concurrent client operations
- Test DataNode failure scenarios

## Monitoring and Debugging

### Logging

All components use SLF4J for structured logging:

```
2025-11-12 12:00:00.000 [pool-1-thread-1] INFO  com.dfs.namenode.NameNode - Write request processed: 5 blocks allocated
2025-11-12 12:00:00.500 [pool-2-thread-3] INFO  com.dfs.datanode.DataNode - Block written successfully: block-uuid-123
```

### Metrics to Monitor

**NameNode:**
- Number of files
- Number of blocks
- Active DataNodes
- Memory usage
- Request latency

**DataNode:**
- Disk usage
- Block count
- Heartbeat status
- Read/write throughput

**Client:**
- Operation success rate
- Latency per operation
- Retry count

## Comparison with HDFS

| Feature | Our DFS | Apache HDFS |
|---------|---------|-------------|
| Block-based storage | ✅ | ✅ |
| Replication | ✅ (configurable) | ✅ (configurable) |
| Master-worker | ✅ | ✅ |
| Heartbeat mechanism | ✅ | ✅ |
| Checksum verification | ✅ (basic) | ✅ (CRC32) |
| NameNode HA | ❌ | ✅ |
| Rack awareness | ❌ | ✅ |
| Erasure coding | ❌ | ✅ |
| Security | ❌ | ✅ (Kerberos) |
| Snapshots | ❌ | ✅ |
| Federation | ❌ | ✅ |

## Conclusion

This DFS implementation provides the core functionality of a distributed file system suitable for:
- Educational purposes
- Understanding distributed systems concepts
- Small to medium-scale deployments
- Testing and development

For production use at massive scale (1000000 PB), additional enhancements for high availability, persistence, security, and monitoring are recommended.
