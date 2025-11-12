# Quick Start Guide

Get started with the Distributed File Storage System in 5 minutes!

## Prerequisites

- Java 21 installed
- Maven 3.6+ installed
- Internet connection for first build (to download dependencies)

## Step 1: Build the Project

```bash
mvn clean package
```

This will compile the code and create the JAR file in `target/` directory.

## Step 2: Start the Cluster

Open a terminal and run:

```bash
./scripts/start-cluster.sh
```

This starts:
- 1 NameNode on port 9000
- 3 DataNodes on ports 9001, 9002, 9003

Logs will be in the `logs/` directory.

## Step 3: Use the Client

In a new terminal, run:

```bash
./scripts/run-client.sh
```

### Example Session

```
dfs> put myfile.txt /myfile.txt
Writing file: myfile.txt -> /myfile.txt
Success!

dfs> list /
Listing files in: /
Path                                               Size       Blocks
-----------------------------------------------------------------------------
/myfile.txt                                        1024            1

dfs> get /myfile.txt downloaded.txt
Reading file: /myfile.txt -> downloaded.txt
Success!

dfs> info /myfile.txt
File: /myfile.txt
Size: 1024 bytes
Blocks: 1
Replication: 3
Created: 2025-11-12 12:00:00.000

dfs> delete /myfile.txt
Deleting file: /myfile.txt
Success!

dfs> exit
Goodbye!
```

## Step 4: Stop the Cluster

```bash
./scripts/stop-cluster.sh
```

## Testing with Large Files

Create a large test file (100 MB):

```bash
dd if=/dev/zero of=large.dat bs=1M count=100
```

Upload to DFS:

```bash
# Start client
./scripts/run-client.sh

# In the client
dfs> put large.dat /large.dat
```

The file will be automatically split into blocks and replicated across DataNodes!

## Running Tests

```bash
mvn test
```

Or use the script:

```bash
./scripts/run-tests.sh
```

## Manual Start (Alternative)

If you prefer to start components manually:

**Terminal 1 - NameNode:**
```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.namenode.NameNode
```

**Terminal 2-4 - DataNodes:**
```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode 9001
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode 9002
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode 9003
```

**Terminal 5 - Client:**
```bash
java -cp target/distributed-file-storage-1.0.0.jar com.dfs.Main client
```

## Programmatic Usage

```java
import com.dfs.client.DFSClient;
import com.dfs.config.DFSConfiguration;

public class Example {
    public static void main(String[] args) {
        // Create client
        DFSConfiguration config = new DFSConfiguration();
        DFSClient client = new DFSClient(config);

        // Upload file
        boolean success = client.writeFile("local.txt", "/remote.txt");

        // Download file
        success = client.readFile("/remote.txt", "downloaded.txt");

        // List files
        var files = client.listFiles("/");
        files.forEach(f -> System.out.println(f.getFilePath()));

        // Delete file
        success = client.deleteFile("/remote.txt");
    }
}
```

## Configuration

Edit `src/main/resources/dfs.properties` to customize:

- Block size
- Replication factor
- Ports
- Data directory
- Heartbeat intervals

## Troubleshooting

**Problem: "Connection refused"**
- Make sure NameNode is running
- Check the port is correct (default: 9000)

**Problem: "No DataNodes available"**
- Make sure at least one DataNode is running
- Wait a few seconds for DataNodes to register with NameNode

**Problem: "File already exists"**
- Delete the existing file first using `delete` command
- Or use a different file path

**Problem: Build fails**
- Ensure Java 21 is installed: `java -version`
- Ensure Maven is installed: `mvn -version`
- Check internet connection for downloading dependencies

## Next Steps

- Read the full [README.md](README.md) for detailed documentation
- Check the architecture and design
- Explore the source code
- Run the comprehensive test suite
- Try deploying across multiple machines

## Performance Tips

For handling large files efficiently:

1. Increase block size for very large files:
   ```properties
   dfs.block.size=268435456  # 256 MB
   ```

2. Adjust replication factor based on needs:
   ```properties
   dfs.replication.factor=3  # Balance between safety and storage
   ```

3. Run DataNodes on separate machines for true distributed storage

4. Monitor logs for performance insights

## Support

For issues or questions, check the logs in `logs/` directory.

Happy distributed file storing!
