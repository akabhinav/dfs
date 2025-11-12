package com.dfs.datanode;

import com.dfs.common.DataNodeInfo;
import com.dfs.config.DFSConfiguration;
import com.dfs.network.Message;
import com.dfs.network.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DataNode - Worker node that stores actual data blocks.
 * Responsibilities:
 * - Store data blocks on local disk
 * - Serve block data to clients
 * - Send heartbeats to NameNode
 * - Report block information to NameNode
 * - Handle replication requests
 */
public class DataNode {
    private static final Logger logger = LoggerFactory.getLogger(DataNode.class);

    private final DFSConfiguration config;
    private final String nameNodeHost;
    private final int nameNodePort;
    private final int dataNodePort;
    private final String dataDir;
    private final AtomicBoolean running;

    private DataNodeInfo nodeInfo;
    private ServerSocket serverSocket;

    private final ExecutorService requestExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    private final Map<String, BlockInfo> blockStorage; // blockId -> BlockInfo

    private static class BlockInfo {
        final String blockId;
        final String filePath;
        final long size;
        final String checksum;

        BlockInfo(String blockId, String filePath, long size, String checksum) {
            this.blockId = blockId;
            this.filePath = filePath;
            this.size = size;
            this.checksum = checksum;
        }
    }

    public DataNode(DFSConfiguration config, int dataNodePort) throws IOException {
        this.config = config;
        this.nameNodeHost = config.getNameNodeHost();
        this.nameNodePort = config.getNameNodePort();
        this.dataNodePort = dataNodePort;
        this.dataDir = config.getDataDir() + "/datanode-" + dataNodePort;
        this.running = new AtomicBoolean(false);

        this.requestExecutor = Executors.newFixedThreadPool(10);
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);

        this.blockStorage = new ConcurrentHashMap<>();

        // Create data directory
        Path dataDirPath = Paths.get(dataDir);
        Files.createDirectories(dataDirPath);

        // Calculate storage space
        File dataDirectory = dataDirPath.toFile();
        long totalSpace = dataDirectory.getTotalSpace();
        long freeSpace = dataDirectory.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;

        this.nodeInfo = new DataNodeInfo("localhost", dataNodePort, totalSpace);
        this.nodeInfo.setUsedSpace(usedSpace);
        this.nodeInfo.setAvailableSpace(freeSpace);

        logger.info("DataNode initialized with data directory: {}", dataDir);
    }

    public void start() throws IOException {
        if (running.get()) {
            logger.warn("DataNode is already running");
            return;
        }

        // Register with NameNode
        registerWithNameNode();

        // Start server socket
        serverSocket = new ServerSocket(dataNodePort);
        running.set(true);

        logger.info("DataNode started on port {}", dataNodePort);

        // Start heartbeat
        startHeartbeat();

        // Load existing blocks
        loadExistingBlocks();

        // Send initial block report
        sendBlockReport();

        // Accept client connections
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                requestExecutor.submit(() -> handleRequest(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    private void registerWithNameNode() {
        try {
            Message registerMsg = new Message(Message.MessageType.REGISTER_DATANODE);
            registerMsg.put("nodeInfo", nodeInfo);

            Message response = NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, registerMsg);

            if (response.get("success").equals(true)) {
                String nodeId = (String) response.get("nodeId");
                logger.info("Successfully registered with NameNode. Node ID: {}", nodeId);
            } else {
                logger.error("Failed to register with NameNode");
            }
        } catch (Exception e) {
            logger.error("Error registering with NameNode", e);
        }
    }

    private void startHeartbeat() {
        long interval = config.getHeartbeatInterval();

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                updateStorageInfo();

                Message heartbeat = new Message(Message.MessageType.HEARTBEAT);
                heartbeat.put("nodeId", nodeInfo.getNodeId());
                heartbeat.put("availableSpace", nodeInfo.getAvailableSpace());
                heartbeat.put("usedSpace", nodeInfo.getUsedSpace());

                NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, heartbeat);

                logger.debug("Heartbeat sent to NameNode");
            } catch (Exception e) {
                logger.error("Error sending heartbeat", e);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    private void sendBlockReport() {
        try {
            Message blockReport = new Message(Message.MessageType.BLOCK_REPORT);
            blockReport.put("nodeId", nodeInfo.getNodeId());
            blockReport.put("blockIds", new ArrayList<>(blockStorage.keySet()));

            NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, blockReport);

            logger.info("Block report sent to NameNode: {} blocks", blockStorage.size());
        } catch (Exception e) {
            logger.error("Error sending block report", e);
        }
    }

    private void loadExistingBlocks() {
        try {
            Path dataDirPath = Paths.get(dataDir);
            if (Files.exists(dataDirPath)) {
                Files.walk(dataDirPath)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".block"))
                        .forEach(blockPath -> {
                            try {
                                String fileName = blockPath.getFileName().toString();
                                String blockId = fileName.replace(".block", "");
                                long size = Files.size(blockPath);

                                byte[] data = Files.readAllBytes(blockPath);
                                String checksum = NetworkUtils.calculateChecksum(data);

                                BlockInfo blockInfo = new BlockInfo(blockId, blockPath.toString(), size, checksum);
                                blockStorage.put(blockId, blockInfo);

                                logger.debug("Loaded existing block: {}", blockId);
                            } catch (IOException e) {
                                logger.error("Error loading block: {}", blockPath, e);
                            }
                        });

                logger.info("Loaded {} existing blocks", blockStorage.size());
            }
        } catch (IOException e) {
            logger.error("Error loading existing blocks", e);
        }
    }

    private void updateStorageInfo() {
        try {
            File dataDirectory = new File(dataDir);
            long totalSpace = dataDirectory.getTotalSpace();
            long freeSpace = dataDirectory.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;

            nodeInfo.setTotalSpace(totalSpace);
            nodeInfo.setAvailableSpace(freeSpace);
            nodeInfo.setUsedSpace(usedSpace);
        } catch (Exception e) {
            logger.error("Error updating storage info", e);
        }
    }

    private void handleRequest(Socket socket) {
        try (socket;
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            Message request = (Message) in.readObject();
            logger.debug("Received request: {}", request.getType());

            Message response = processRequest(request, socket);

            out.writeObject(response);
            out.flush();

        } catch (Exception e) {
            logger.error("Error handling request", e);
        }
    }

    private Message processRequest(Message request, Socket socket) {
        return switch (request.getType()) {
            case WRITE_BLOCK -> handleWriteBlock(request, socket);
            case READ_BLOCK -> handleReadBlock(request, socket);
            case DELETE_BLOCK -> handleDeleteBlock(request);
            default -> {
                Message error = new Message(Message.MessageType.ERROR);
                error.put("message", "Unknown request type: " + request.getType());
                yield error;
            }
        };
    }

    private Message handleWriteBlock(Message request, Socket socket) {
        String blockId = (String) request.get("blockId");
        long blockSize = (Long) request.get("blockSize");

        logger.info("Writing block: {}, size: {}", blockId, blockSize);

        try {
            // Receive block data
            byte[] data = receiveBlockData(socket, (int) blockSize);

            // Calculate checksum
            String checksum = NetworkUtils.calculateChecksum(data);

            // Write to disk
            String blockFilePath = dataDir + "/" + blockId + ".block";
            try (FileOutputStream fos = new FileOutputStream(blockFilePath)) {
                fos.write(data);
            }

            // Store block info
            BlockInfo blockInfo = new BlockInfo(blockId, blockFilePath, blockSize, checksum);
            blockStorage.put(blockId, blockInfo);

            // Update storage info
            updateStorageInfo();

            logger.info("Block written successfully: {}", blockId);

            Message response = new Message(Message.MessageType.BLOCK_ACK);
            response.put("success", true);
            response.put("blockId", blockId);
            response.put("checksum", checksum);

            return response;

        } catch (Exception e) {
            logger.error("Error writing block: {}", blockId, e);
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "Failed to write block: " + e.getMessage());
            return error;
        }
    }

    private Message handleReadBlock(Message request, Socket socket) {
        String blockId = (String) request.get("blockId");

        logger.info("Reading block: {}", blockId);

        BlockInfo blockInfo = blockStorage.get(blockId);
        if (blockInfo == null) {
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "Block not found: " + blockId);
            return error;
        }

        try {
            // Read block data from disk
            byte[] data = Files.readAllBytes(Paths.get(blockInfo.filePath));

            // Verify checksum
            if (!NetworkUtils.verifyChecksum(data, blockInfo.checksum)) {
                logger.error("Checksum verification failed for block: {}", blockId);
                Message error = new Message(Message.MessageType.ERROR);
                error.put("message", "Checksum verification failed");
                return error;
            }

            // Send block data
            sendBlockData(socket, data);

            logger.info("Block read successfully: {}", blockId);

            Message response = new Message(Message.MessageType.BLOCK_DATA);
            response.put("success", true);
            response.put("blockId", blockId);
            response.put("size", data.length);
            response.put("checksum", blockInfo.checksum);

            return response;

        } catch (Exception e) {
            logger.error("Error reading block: {}", blockId, e);
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "Failed to read block: " + e.getMessage());
            return error;
        }
    }

    private Message handleDeleteBlock(Message request) {
        String blockId = (String) request.get("blockId");

        logger.info("Deleting block: {}", blockId);

        BlockInfo blockInfo = blockStorage.remove(blockId);
        if (blockInfo == null) {
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "Block not found: " + blockId);
            return error;
        }

        try {
            Files.deleteIfExists(Paths.get(blockInfo.filePath));
            updateStorageInfo();

            logger.info("Block deleted successfully: {}", blockId);

            Message response = new Message(Message.MessageType.SUCCESS);
            response.put("success", true);

            return response;

        } catch (Exception e) {
            logger.error("Error deleting block: {}", blockId, e);
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "Failed to delete block: " + e.getMessage());
            return error;
        }
    }

    private byte[] receiveBlockData(Socket socket, int size) throws IOException {
        InputStream in = socket.getInputStream();
        byte[] data = new byte[size];
        int totalRead = 0;

        while (totalRead < size) {
            int read = in.read(data, totalRead, size - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream");
            }
            totalRead += read;
        }

        return data;
    }

    private void sendBlockData(Socket socket, byte[] data) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
    }

    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        requestExecutor.shutdown();
        heartbeatExecutor.shutdown();

        logger.info("DataNode stopped");
    }

    public DataNodeInfo getNodeInfo() {
        return nodeInfo;
    }

    public int getBlockCount() {
        return blockStorage.size();
    }

    public static void main(String[] args) {
        try {
            DFSConfiguration config = new DFSConfiguration();
            int port = config.getDataNodePort();

            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }

            DataNode dataNode = new DataNode(config, port);
            dataNode.start();
        } catch (Exception e) {
            logger.error("Failed to start DataNode", e);
            System.exit(1);
        }
    }
}
