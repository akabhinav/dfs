package com.dfs.namenode;

import com.dfs.common.*;
import com.dfs.config.DFSConfiguration;
import com.dfs.network.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NameNode - Master server that manages file system metadata.
 * Responsibilities:
 * - Manage file system namespace
 * - Track file-to-block mapping
 * - Track block-to-datanode mapping
 * - Handle client requests
 * - Monitor DataNode health
 * - Manage replication
 */
public class NameNode {
    private static final Logger logger = LoggerFactory.getLogger(NameNode.class);

    private final DFSConfiguration config;
    private final int port;
    private final AtomicBoolean running;

    // Metadata storage
    private final Map<String, FileMetadata> fileMetadataMap; // filePath -> FileMetadata
    private final Map<String, Block> blockMap; // blockId -> Block
    private final Map<String, BlockLocation> blockLocationMap; // blockId -> BlockLocation
    private final Map<String, DataNodeInfo> dataNodeMap; // nodeId -> DataNodeInfo

    // Thread pools
    private final ExecutorService requestExecutor;
    private final ScheduledExecutorService heartbeatMonitor;

    private ServerSocket serverSocket;

    public NameNode(DFSConfiguration config) {
        this.config = config;
        this.port = config.getNameNodePort();
        this.running = new AtomicBoolean(false);

        this.fileMetadataMap = new ConcurrentHashMap<>();
        this.blockMap = new ConcurrentHashMap<>();
        this.blockLocationMap = new ConcurrentHashMap<>();
        this.dataNodeMap = new ConcurrentHashMap<>();

        this.requestExecutor = Executors.newFixedThreadPool(10);
        this.heartbeatMonitor = Executors.newScheduledThreadPool(1);
    }

    public void start() throws IOException {
        if (running.get()) {
            logger.warn("NameNode is already running");
            return;
        }

        serverSocket = new ServerSocket(port);
        running.set(true);

        logger.info("NameNode started on port {}", port);

        // Start heartbeat monitoring
        startHeartbeatMonitoring();

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

    private void handleRequest(Socket socket) {
        try (socket;
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            Message request = (Message) in.readObject();
            logger.debug("Received request: {}", request.getType());

            Message response = processRequest(request);

            out.writeObject(response);
            out.flush();

        } catch (Exception e) {
            logger.error("Error handling request", e);
        }
    }

    private Message processRequest(Message request) {
        return switch (request.getType()) {
            case WRITE_REQUEST -> handleWriteRequest(request);
            case READ_REQUEST -> handleReadRequest(request);
            case DELETE_REQUEST -> handleDeleteRequest(request);
            case LIST_REQUEST -> handleListRequest(request);
            case GET_FILE_INFO -> handleGetFileInfo(request);
            case REGISTER_DATANODE -> handleDataNodeRegistration(request);
            case HEARTBEAT -> handleHeartbeat(request);
            case BLOCK_REPORT -> handleBlockReport(request);
            default -> {
                Message error = new Message(Message.MessageType.ERROR);
                error.put("message", "Unknown request type: " + request.getType());
                yield error;
            }
        };
    }

    private Message handleWriteRequest(Message request) {
        String filePath = (String) request.get("filePath");
        long fileSize = (Long) request.get("fileSize");
        int replicationFactor = config.getReplicationFactor();

        logger.info("Write request for file: {}, size: {}", filePath, fileSize);

        // Check if file already exists
        if (fileMetadataMap.containsKey(filePath)) {
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "File already exists: " + filePath);
            return error;
        }

        // Calculate number of blocks needed
        long blockSize = config.getBlockSize();
        int numBlocks = (int) Math.ceil((double) fileSize / blockSize);

        // Create blocks and assign to DataNodes
        List<String> blockIds = new ArrayList<>();
        Map<String, List<DataNodeInfo>> blockToDataNodes = new HashMap<>();

        for (int i = 0; i < numBlocks; i++) {
            long currentBlockSize = Math.min(blockSize, fileSize - (i * blockSize));
            Block block = Block.create(currentBlockSize, "");
            String blockId = block.getBlockId();

            blockIds.add(blockId);
            blockMap.put(blockId, block);

            // Select DataNodes for this block
            List<DataNodeInfo> selectedNodes = selectDataNodesForBlock(replicationFactor);
            blockToDataNodes.put(blockId, selectedNodes);

            // Update block location
            BlockLocation location = new BlockLocation(blockId);
            for (DataNodeInfo node : selectedNodes) {
                location.addDataNode(node.getNodeId());
            }
            blockLocationMap.put(blockId, location);
        }

        // Create file metadata
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        FileMetadata metadata = new FileMetadata(fileName, filePath, fileSize, blockIds, replicationFactor);
        fileMetadataMap.put(filePath, metadata);

        // Prepare response
        Message response = new Message(Message.MessageType.WRITE_RESPONSE);
        response.put("success", true);
        response.put("blockToDataNodes", blockToDataNodes);
        response.put("blockIds", blockIds);
        response.put("blockSize", blockSize);

        logger.info("Write request processed: {} blocks allocated", numBlocks);

        return response;
    }

    private Message handleReadRequest(Message request) {
        String filePath = (String) request.get("filePath");

        logger.info("Read request for file: {}", filePath);

        FileMetadata metadata = fileMetadataMap.get(filePath);
        if (metadata == null) {
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "File not found: " + filePath);
            return error;
        }

        // Get block locations
        Map<String, List<DataNodeInfo>> blockToDataNodes = new HashMap<>();
        for (String blockId : metadata.getBlockIds()) {
            BlockLocation location = blockLocationMap.get(blockId);
            if (location != null) {
                List<DataNodeInfo> nodes = new ArrayList<>();
                for (String nodeId : location.getDataNodeIds()) {
                    DataNodeInfo node = dataNodeMap.get(nodeId);
                    if (node != null && node.isActive()) {
                        nodes.add(node);
                    }
                }
                blockToDataNodes.put(blockId, nodes);
            }
        }

        Message response = new Message(Message.MessageType.READ_RESPONSE);
        response.put("success", true);
        response.put("metadata", metadata);
        response.put("blockToDataNodes", blockToDataNodes);

        logger.info("Read request processed: {} blocks", metadata.getBlockIds().size());

        return response;
    }

    private Message handleDeleteRequest(Message request) {
        String filePath = (String) request.get("filePath");

        logger.info("Delete request for file: {}", filePath);

        FileMetadata metadata = fileMetadataMap.remove(filePath);
        if (metadata == null) {
            Message error = new Message(Message.MessageType.ERROR);
            error.put("message", "File not found: " + filePath);
            return error;
        }

        // Remove blocks
        for (String blockId : metadata.getBlockIds()) {
            blockMap.remove(blockId);
            blockLocationMap.remove(blockId);
        }

        Message response = new Message(Message.MessageType.DELETE_RESPONSE);
        response.put("success", true);

        logger.info("Delete request processed for file: {}", filePath);

        return response;
    }

    private Message handleListRequest(Message request) {
        String directory = (String) request.get("directory");
        if (directory == null) {
            directory = "/";
        }

        logger.info("List request for directory: {}", directory);

        List<FileMetadata> files = new ArrayList<>();
        for (FileMetadata metadata : fileMetadataMap.values()) {
            if (metadata.getFilePath().startsWith(directory)) {
                files.add(metadata);
            }
        }

        Message response = new Message(Message.MessageType.LIST_RESPONSE);
        response.put("success", true);
        response.put("files", files);

        return response;
    }

    private Message handleGetFileInfo(Message request) {
        String filePath = (String) request.get("filePath");

        FileMetadata metadata = fileMetadataMap.get(filePath);

        Message response = new Message(Message.MessageType.FILE_INFO_RESPONSE);
        if (metadata != null) {
            response.put("success", true);
            response.put("metadata", metadata);
        } else {
            response.put("success", false);
            response.put("message", "File not found: " + filePath);
        }

        return response;
    }

    private Message handleDataNodeRegistration(Message request) {
        DataNodeInfo nodeInfo = (DataNodeInfo) request.get("nodeInfo");

        logger.info("DataNode registration: {}", nodeInfo);

        dataNodeMap.put(nodeInfo.getNodeId(), nodeInfo);

        Message response = new Message(Message.MessageType.REGISTER_ACK);
        response.put("success", true);
        response.put("nodeId", nodeInfo.getNodeId());

        return response;
    }

    private Message handleHeartbeat(Message request) {
        String nodeId = (String) request.get("nodeId");
        Long availableSpace = (Long) request.get("availableSpace");
        Long usedSpace = (Long) request.get("usedSpace");

        DataNodeInfo nodeInfo = dataNodeMap.get(nodeId);
        if (nodeInfo != null) {
            nodeInfo.updateHeartbeat();
            if (availableSpace != null) {
                nodeInfo.setAvailableSpace(availableSpace);
            }
            if (usedSpace != null) {
                nodeInfo.setUsedSpace(usedSpace);
            }
            nodeInfo.setActive(true);

            logger.debug("Heartbeat received from DataNode: {}", nodeId);
        }

        Message response = new Message(Message.MessageType.SUCCESS);
        response.put("success", true);

        return response;
    }

    private Message handleBlockReport(Message request) {
        String nodeId = (String) request.get("nodeId");
        @SuppressWarnings("unchecked")
        List<String> blockIds = (List<String>) request.get("blockIds");

        logger.info("Block report from DataNode {}: {} blocks", nodeId, blockIds.size());

        // Update block locations
        for (String blockId : blockIds) {
            BlockLocation location = blockLocationMap.get(blockId);
            if (location != null) {
                location.addDataNode(nodeId);
            }
        }

        Message response = new Message(Message.MessageType.SUCCESS);
        response.put("success", true);

        return response;
    }

    private List<DataNodeInfo> selectDataNodesForBlock(int replicationFactor) {
        List<DataNodeInfo> activeNodes = dataNodeMap.values().stream()
                .filter(DataNodeInfo::isActive)
                .sorted(Comparator.comparingLong(DataNodeInfo::getAvailableSpace).reversed())
                .toList();

        int count = Math.min(replicationFactor, activeNodes.size());
        return new ArrayList<>(activeNodes.subList(0, count));
    }

    private void startHeartbeatMonitoring() {
        long timeout = config.getHeartbeatTimeout();

        heartbeatMonitor.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            for (DataNodeInfo node : dataNodeMap.values()) {
                if (currentTime - node.getLastHeartbeat() > timeout) {
                    logger.warn("DataNode {} is inactive (no heartbeat)", node.getNodeId());
                    node.setActive(false);
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS);
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
        heartbeatMonitor.shutdown();

        logger.info("NameNode stopped");
    }

    public Map<String, FileMetadata> getFileMetadataMap() {
        return new HashMap<>(fileMetadataMap);
    }

    public Map<String, DataNodeInfo> getDataNodeMap() {
        return new HashMap<>(dataNodeMap);
    }

    public static void main(String[] args) {
        try {
            DFSConfiguration config = new DFSConfiguration();
            if (args.length > 0) {
                config.setProperty("dfs.namenode.port", args[0]);
            }

            NameNode nameNode = new NameNode(config);
            nameNode.start();
        } catch (Exception e) {
            logger.error("Failed to start NameNode", e);
            System.exit(1);
        }
    }
}
