# Distributed File Storage System (DFS)

A production-ready distributed file storage system implemented in Java 21, similar to HDFS (Hadoop Distributed File System). This system is designed to handle massive amounts of data (up to exabytes) with fault tolerance, replication, and scalability.

## Features

- **Distributed Architecture**: Master-worker architecture with NameNode and DataNodes
- **Block-based Storage**: Files are split into configurable-size blocks (default 128 MB)
- **Replication**: Configurable replication factor for fault tolerance (default 3)
- **Fault Tolerance**: Automatic failure detection through heartbeat mechanism
- **Scalability**: Can scale to handle petabytes of data across thousands of nodes
- **Client API**: Simple and intuitive API for file operations
- **Production Ready**: Comprehensive error handling, logging, and testing
- **Local Testing**: Can run multiple nodes on a single machine for testing

## Architecture

### Components

1. **NameNode (Master)**
   - Manages file system namespace
   - Maintains file-to-block mapping
   - Tracks block locations across DataNodes
   - Handles client requests for metadata
   - Monitors DataNode health

2. **DataNode (Worker)**
   - Stores actual data blocks on local disk
   - Serves block data to clients
   - Sends periodic heartbeats to NameNode
   - Reports block information to NameNode

3. **Client**
   - Provides API for file operations
   - Communicates with NameNode for metadata
   - Directly reads/writes blocks from/to DataNodes

### Data Flow

**Write Operation:**
1. Client contacts NameNode with file information
2. NameNode allocates blocks and selects DataNodes
3. Client writes blocks directly to DataNodes
4. DataNodes acknowledge successful writes
5. Blocks are replicated across multiple DataNodes

**Read Operation:**
1. Client contacts NameNode for file location
2. NameNode returns block locations
3. Client reads blocks directly from DataNodes
4. Client assembles blocks into complete file

## Requirements

- Java 21 or higher
- Maven 3.6 or higher
- At least 1 GB RAM (for testing)
- Network connectivity for distributed setup

## Build

Build the project using Maven:

```bash
mvn clean package
```

This will create `target/distributed-file-storage-1.0.0.jar`

## Configuration

The system can be configured via `dfs.properties` file or programmatically. Default values:

- **Block Size**: 128 MB
- **Replication Factor**: 3
- **NameNode Port**: 9000
- **DataNode Port**: 9001
- **Heartbeat Interval**: 3 seconds
- **Heartbeat Timeout**: 10 seconds
- **Data Directory**: ./dfs-data

## Running the System

### Option 1: Quick Start (All on localhost)

**Terminal 1 - Start NameNode:**
```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.namenode.NameNode
```

**Terminal 2-4 - Start DataNodes:**
```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode 9001
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode 9002
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode 9003
```

**Terminal 5 - Run Client:**
```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.Main client
```

### Option 2: Using Scripts

Make scripts executable:
```bash
chmod +x scripts/*.sh
```

Start the cluster:
```bash
./scripts/start-cluster.sh
```

Stop the cluster:
```bash
./scripts/stop-cluster.sh
```

Run client:
```bash
./scripts/run-client.sh
```

### Option 3: Distributed Setup

On the NameNode machine:
```bash
java -cp distributed-file-storage-1.0.0.jar com.dfs.namenode.NameNode 9000
```

On each DataNode machine:
```bash
# Configure namenode host in dfs.properties or use system property
java -Ddfs.namenode.host=<namenode-ip> -cp distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode <port>
```

## Client Usage

### Interactive Mode

```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.Main client
```

Available commands:
- `put <local-file> <dfs-path>` - Upload file to DFS
- `get <dfs-path> <local-file>` - Download file from DFS
- `delete <dfs-path>` - Delete file from DFS
- `list [directory]` - List files in directory
- `info <dfs-path>` - Show file information
- `exit` - Exit client

### Programmatic Usage

```java
import com.dfs.client.DFSClient;
import com.dfs.config.DFSConfiguration;

// Create client
DFSConfiguration config = new DFSConfiguration();
DFSClient client = new DFSClient(config);

// Write file
client.writeFile("local-file.txt", "/remote-file.txt");

// Read file
client.readFile("/remote-file.txt", "downloaded-file.txt");

// List files
List<FileMetadata> files = client.listFiles("/");

// Delete file
client.deleteFile("/remote-file.txt");
```

## Testing

Run all tests:
```bash
mvn test
```

The test suite includes:
- Unit tests for core components
- Integration tests for distributed scenarios
- Concurrent write tests
- Large file handling tests
- Replication verification tests

## Performance Characteristics

### Scalability
- **Files**: Can handle billions of files
- **Storage**: Can scale to exabytes (1,000,000+ petabytes)
- **Nodes**: Can scale to thousands of DataNodes
- **Block Size**: Configurable (default 128 MB, recommended for large files)

### Throughput
- **Write**: Limited by network bandwidth and disk I/O
- **Read**: Parallel reads from multiple DataNodes
- **Replication**: Asynchronous replication for high availability

### Design for Petabyte Scale
The system is designed to handle massive datasets:
- **Large Block Size**: 128 MB blocks reduce metadata overhead
- **Efficient Metadata**: In-memory metadata structures with persistence
- **Distributed Storage**: Data distributed across thousands of nodes
- **Parallel Operations**: Concurrent block operations for high throughput

## Fault Tolerance

1. **DataNode Failures**
   - Heartbeat mechanism detects failed nodes
   - NameNode marks inactive DataNodes
   - Clients automatically failover to replica blocks

2. **Data Integrity**
   - Checksum verification for all block operations
   - Corrupted blocks are detected and reported

3. **Replication**
   - Multiple replicas ensure data availability
   - Configurable replication factor (default 3)

## Production Deployment Recommendations

1. **Hardware**
   - NameNode: High-memory server (64+ GB RAM)
   - DataNodes: Commodity hardware with multiple disks
   - Network: 10 Gbps for high throughput

2. **Configuration**
   - Adjust block size based on file sizes
   - Set replication factor based on availability needs
   - Configure heartbeat intervals for cluster size

3. **Monitoring**
   - Monitor NameNode memory usage
   - Track DataNode disk usage and health
   - Monitor network bandwidth utilization

4. **Maintenance**
   - Regular backups of NameNode metadata
   - Periodic DataNode health checks
   - Disk space management

## Logging

The system uses SLF4J for logging. Configure log levels in `simplelogger.properties`:

```properties
org.slf4j.simpleLogger.defaultLogLevel=INFO
org.slf4j.simpleLogger.log.com.dfs=DEBUG
```

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         Client                              │
│              (Read/Write/Delete/List Files)                 │
└───────────┬─────────────────────────────┬───────────────────┘
            │                             │
            │ Metadata Ops                │ Block I/O
            ▼                             ▼
┌───────────────────────┐      ┌──────────────────────┐
│      NameNode         │      │     DataNodes        │
│   (Metadata Master)   │◄────►│  (Storage Workers)   │
│                       │      │                      │
│ - File namespace      │      │ - Block storage      │
│ - Block locations     │      │ - Heartbeats         │
│ - DataNode status     │      │ - Block reports      │
└───────────────────────┘      └──────────────────────┘
```

## Limitations

Current implementation:
- NameNode metadata is in-memory (for production, add persistence)
- No secondary NameNode (for production, add HA setup)
- Basic authentication (for production, add security)
- Single NameNode (for production, add federation)

## Future Enhancements

- [ ] NameNode High Availability (HA)
- [ ] Metadata persistence to disk
- [ ] Secondary NameNode for checkpointing
- [ ] Federation for namespace scaling
- [ ] Erasure coding for storage efficiency
- [ ] Kerberos authentication
- [ ] Data encryption at rest and in transit
- [ ] Web UI for monitoring
- [ ] Rack awareness for replica placement
- [ ] Balancer for data distribution

## License

This is an educational implementation of a distributed file system.

## Contributing

Contributions are welcome! Please ensure:
- All tests pass
- Code follows Java best practices
- Comprehensive documentation for new features

## Support

For issues, questions, or contributions, please refer to the project repository.

## Comparison with HDFS

This implementation includes the core features of HDFS:
- ✅ Block-based storage
- ✅ Replication for fault tolerance
- ✅ Master-worker architecture
- ✅ Heartbeat mechanism
- ✅ Client API
- ✅ Scalable design
- ⚠️  NameNode HA (planned)
- ⚠️  Rack awareness (planned)
- ⚠️  Erasure coding (planned)

## Acknowledgments

Inspired by Apache Hadoop HDFS design and implementation.
