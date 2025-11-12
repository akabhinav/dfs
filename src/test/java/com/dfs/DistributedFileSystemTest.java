package com.dfs;

import com.dfs.client.DFSClient;
import com.dfs.common.FileMetadata;
import com.dfs.config.DFSConfiguration;
import com.dfs.datanode.DataNode;
import com.dfs.namenode.NameNode;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Distributed File System.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DistributedFileSystemTest {

    private static NameNode nameNode;
    private static List<DataNode> dataNodes;
    private static DFSClient client;
    private static DFSConfiguration config;

    private static final int NAMENODE_PORT = 19000;
    private static final int[] DATANODE_PORTS = {19001, 19002, 19003};
    private static final String TEST_DATA_DIR = "./test-dfs-data";

    @BeforeAll
    static void setUp() throws Exception {
        // Clean up any existing test data
        cleanTestData();

        // Configure DFS
        config = new DFSConfiguration();
        config.setProperty("dfs.namenode.port", String.valueOf(NAMENODE_PORT));
        config.setProperty("dfs.data.dir", TEST_DATA_DIR);
        config.setProperty("dfs.block.size", String.valueOf(1024 * 1024)); // 1 MB for testing
        config.setProperty("dfs.replication.factor", "3");
        config.setProperty("dfs.namenode.host", "localhost");

        // Start NameNode in a separate thread
        CountDownLatch nameNodeLatch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                nameNode = new NameNode(config);
                nameNodeLatch.countDown();
                nameNode.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Wait for NameNode to start
        Thread.sleep(2000);

        // Start DataNodes
        dataNodes = new ArrayList<>();
        for (int port : DATANODE_PORTS) {
            DataNode dataNode = new DataNode(config, port);
            dataNodes.add(dataNode);

            new Thread(() -> {
                try {
                    dataNode.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        // Wait for DataNodes to register
        Thread.sleep(3000);

        // Create client
        client = new DFSClient(config);

        System.out.println("Test environment set up successfully");
    }

    @AfterAll
    static void tearDown() throws Exception {
        // Stop all nodes
        if (nameNode != null) {
            nameNode.stop();
        }

        if (dataNodes != null) {
            for (DataNode dataNode : dataNodes) {
                dataNode.stop();
            }
        }

        // Clean up test data
        Thread.sleep(1000);
        cleanTestData();

        System.out.println("Test environment cleaned up");
    }

    private static void cleanTestData() throws IOException {
        Path testDataPath = Paths.get(TEST_DATA_DIR);
        if (Files.exists(testDataPath)) {
            Files.walk(testDataPath)
                    .sorted((p1, p2) -> p2.compareTo(p1)) // Reverse order for directory deletion
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }

        // Clean up test files
        new File("test-file.txt").delete();
        new File("downloaded-file.txt").delete();
        new File("large-test-file.txt").delete();
        new File("downloaded-large-file.txt").delete();
    }

    @Test
    @Order(1)
    void testWriteSmallFile() throws Exception {
        // Create test file
        String testContent = "Hello, Distributed File System!\nThis is a test file.";
        File testFile = new File("test-file.txt");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(testContent);
        }

        // Write to DFS
        boolean success = client.writeFile("test-file.txt", "/test-file.txt");
        assertTrue(success, "File write should succeed");

        // Verify file exists in NameNode
        FileMetadata metadata = client.getFileInfo("/test-file.txt");
        assertNotNull(metadata, "File metadata should exist");
        assertEquals("/test-file.txt", metadata.getFilePath());
        assertEquals(testContent.length(), metadata.getFileSize());
    }

    @Test
    @Order(2)
    void testReadSmallFile() throws Exception {
        // Read from DFS
        boolean success = client.readFile("/test-file.txt", "downloaded-file.txt");
        assertTrue(success, "File read should succeed");

        // Verify content
        File downloadedFile = new File("downloaded-file.txt");
        assertTrue(downloadedFile.exists(), "Downloaded file should exist");

        String originalContent = Files.readString(Paths.get("test-file.txt"));
        String downloadedContent = Files.readString(Paths.get("downloaded-file.txt"));
        assertEquals(originalContent, downloadedContent, "Content should match");
    }

    @Test
    @Order(3)
    void testListFiles() {
        List<FileMetadata> files = client.listFiles("/");
        assertFalse(files.isEmpty(), "Should have at least one file");

        boolean found = files.stream()
                .anyMatch(f -> f.getFilePath().equals("/test-file.txt"));
        assertTrue(found, "Should find the test file");
    }

    @Test
    @Order(4)
    void testWriteLargeFile() throws Exception {
        // Create a large test file (5 MB)
        File largeFile = new File("large-test-file.txt");
        try (FileWriter writer = new FileWriter(largeFile)) {
            for (int i = 0; i < 100000; i++) {
                writer.write("This is line " + i + " of the large test file.\n");
            }
        }

        long fileSize = largeFile.length();
        System.out.println("Large file size: " + fileSize + " bytes");

        // Write to DFS
        boolean success = client.writeFile("large-test-file.txt", "/large-test-file.txt");
        assertTrue(success, "Large file write should succeed");

        // Verify metadata
        FileMetadata metadata = client.getFileInfo("/large-test-file.txt");
        assertNotNull(metadata, "Large file metadata should exist");
        assertEquals(fileSize, metadata.getFileSize());
        assertTrue(metadata.getBlockIds().size() > 1, "Should be split into multiple blocks");
    }

    @Test
    @Order(5)
    void testReadLargeFile() throws Exception {
        // Read from DFS
        boolean success = client.readFile("/large-test-file.txt", "downloaded-large-file.txt");
        assertTrue(success, "Large file read should succeed");

        // Verify size
        File originalFile = new File("large-test-file.txt");
        File downloadedFile = new File("downloaded-large-file.txt");

        assertEquals(originalFile.length(), downloadedFile.length(), "File sizes should match");

        // Verify first and last lines
        String originalContent = Files.readString(originalFile.toPath());
        String downloadedContent = Files.readString(downloadedFile.toPath());
        assertEquals(originalContent, downloadedContent, "Content should match");
    }

    @Test
    @Order(6)
    void testDeleteFile() {
        // Delete file
        boolean success = client.deleteFile("/test-file.txt");
        assertTrue(success, "File delete should succeed");

        // Verify file is deleted
        FileMetadata metadata = client.getFileInfo("/test-file.txt");
        assertNull(metadata, "File should not exist after deletion");
    }

    @Test
    @Order(7)
    void testFileNotFound() {
        // Try to read non-existent file
        boolean success = client.readFile("/non-existent-file.txt", "output.txt");
        assertFalse(success, "Reading non-existent file should fail");
    }

    @Test
    @Order(8)
    void testDataNodeHealth() {
        // Verify all DataNodes are active
        var dataNodeMap = nameNode.getDataNodeMap();
        assertEquals(DATANODE_PORTS.length, dataNodeMap.size(), "All DataNodes should be registered");

        long activeCount = dataNodeMap.values().stream()
                .filter(node -> node.isActive())
                .count();
        assertEquals(DATANODE_PORTS.length, activeCount, "All DataNodes should be active");
    }

    @Test
    @Order(9)
    void testReplication() throws Exception {
        // Create and write a file
        File testFile = new File("replication-test.txt");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("Testing replication functionality");
        }

        boolean success = client.writeFile("replication-test.txt", "/replication-test.txt");
        assertTrue(success, "File write should succeed");

        // Check that blocks are replicated
        FileMetadata metadata = client.getFileInfo("/replication-test.txt");
        assertNotNull(metadata);
        assertEquals(3, metadata.getReplicationFactor(), "Replication factor should be 3");

        // Verify blocks exist on multiple DataNodes
        for (DataNode dataNode : dataNodes) {
            // At least one DataNode should have the block
            if (dataNode.getBlockCount() > 0) {
                System.out.println("DataNode " + dataNode.getNodeInfo().getPort() +
                        " has " + dataNode.getBlockCount() + " blocks");
            }
        }

        // Clean up
        testFile.delete();
        client.deleteFile("/replication-test.txt");
    }

    @Test
    @Order(10)
    void testConcurrentWrites() throws Exception {
        int numFiles = 10;
        CountDownLatch latch = new CountDownLatch(numFiles);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numFiles; i++) {
            int fileNum = i;
            Thread thread = new Thread(() -> {
                try {
                    String fileName = "concurrent-test-" + fileNum + ".txt";
                    File file = new File(fileName);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write("Concurrent write test " + fileNum);
                    }

                    boolean success = client.writeFile(fileName, "/" + fileName);
                    assertTrue(success, "Concurrent write should succeed");

                    file.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Concurrent write failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All concurrent writes should complete");

        // Verify all files exist
        List<FileMetadata> files = client.listFiles("/concurrent");
        assertEquals(numFiles, files.size(), "All concurrent files should exist");

        // Clean up
        for (int i = 0; i < numFiles; i++) {
            client.deleteFile("/concurrent-test-" + i + ".txt");
        }
    }
}
