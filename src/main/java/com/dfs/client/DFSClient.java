package com.dfs.client;

import com.dfs.common.DataNodeInfo;
import com.dfs.common.FileMetadata;
import com.dfs.config.DFSConfiguration;
import com.dfs.network.Message;
import com.dfs.network.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Client API for interacting with the distributed file system.
 * Provides methods to:
 * - Write files to DFS
 * - Read files from DFS
 * - Delete files from DFS
 * - List files in DFS
 */
public class DFSClient {
    private static final Logger logger = LoggerFactory.getLogger(DFSClient.class);

    private final DFSConfiguration config;
    private final String nameNodeHost;
    private final int nameNodePort;

    public DFSClient(DFSConfiguration config) {
        this.config = config;
        this.nameNodeHost = config.getNameNodeHost();
        this.nameNodePort = config.getNameNodePort();
    }

    /**
     * Write a file to the distributed file system.
     *
     * @param localFilePath Path to the local file
     * @param dfsFilePath   Destination path in DFS
     * @return true if successful, false otherwise
     */
    public boolean writeFile(String localFilePath, String dfsFilePath) {
        logger.info("Writing file: {} -> {}", localFilePath, dfsFilePath);

        try {
            // Read local file
            File localFile = new File(localFilePath);
            if (!localFile.exists()) {
                logger.error("Local file not found: {}", localFilePath);
                return false;
            }

            long fileSize = localFile.length();
            byte[] fileData = readLocalFile(localFilePath);

            // Request write from NameNode
            Message writeRequest = new Message(Message.MessageType.WRITE_REQUEST);
            writeRequest.put("filePath", dfsFilePath);
            writeRequest.put("fileSize", fileSize);

            Message response = NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, writeRequest);

            if (!Boolean.TRUE.equals(response.get("success"))) {
                logger.error("Write request failed: {}", response.get("message"));
                return false;
            }

            // Get block assignments
            @SuppressWarnings("unchecked")
            Map<String, List<DataNodeInfo>> blockToDataNodes =
                    (Map<String, List<DataNodeInfo>>) response.get("blockToDataNodes");
            @SuppressWarnings("unchecked")
            List<String> blockIds = (List<String>) response.get("blockIds");
            long blockSize = (Long) response.get("blockSize");

            // Write blocks to DataNodes
            int offset = 0;
            for (int i = 0; i < blockIds.size(); i++) {
                String blockId = blockIds.get(i);
                List<DataNodeInfo> dataNodes = blockToDataNodes.get(blockId);

                int currentBlockSize = (int) Math.min(blockSize, fileSize - offset);
                byte[] blockData = Arrays.copyOfRange(fileData, offset, offset + currentBlockSize);

                // Write to all replicas
                boolean success = writeBlockToDataNodes(blockId, blockData, dataNodes);
                if (!success) {
                    logger.error("Failed to write block: {}", blockId);
                    return false;
                }

                offset += currentBlockSize;
                logger.info("Block {}/{} written successfully", i + 1, blockIds.size());
            }

            logger.info("File written successfully: {}", dfsFilePath);
            return true;

        } catch (Exception e) {
            logger.error("Error writing file", e);
            return false;
        }
    }

    /**
     * Read a file from the distributed file system.
     *
     * @param dfsFilePath   Path in DFS
     * @param localFilePath Destination path for local file
     * @return true if successful, false otherwise
     */
    public boolean readFile(String dfsFilePath, String localFilePath) {
        logger.info("Reading file: {} -> {}", dfsFilePath, localFilePath);

        try {
            // Request read from NameNode
            Message readRequest = new Message(Message.MessageType.READ_REQUEST);
            readRequest.put("filePath", dfsFilePath);

            Message response = NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, readRequest);

            if (!Boolean.TRUE.equals(response.get("success"))) {
                logger.error("Read request failed: {}", response.get("message"));
                return false;
            }

            // Get block locations
            FileMetadata metadata = (FileMetadata) response.get("metadata");
            @SuppressWarnings("unchecked")
            Map<String, List<DataNodeInfo>> blockToDataNodes =
                    (Map<String, List<DataNodeInfo>>) response.get("blockToDataNodes");

            // Read blocks from DataNodes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            List<String> blockIds = metadata.getBlockIds();

            for (int i = 0; i < blockIds.size(); i++) {
                String blockId = blockIds.get(i);
                List<DataNodeInfo> dataNodes = blockToDataNodes.get(blockId);

                byte[] blockData = readBlockFromDataNodes(blockId, dataNodes);
                if (blockData == null) {
                    logger.error("Failed to read block: {}", blockId);
                    return false;
                }

                outputStream.write(blockData);
                logger.info("Block {}/{} read successfully", i + 1, blockIds.size());
            }

            // Write to local file
            try (FileOutputStream fos = new FileOutputStream(localFilePath)) {
                fos.write(outputStream.toByteArray());
            }

            logger.info("File read successfully: {}", dfsFilePath);
            return true;

        } catch (Exception e) {
            logger.error("Error reading file", e);
            return false;
        }
    }

    /**
     * Delete a file from the distributed file system.
     *
     * @param dfsFilePath Path in DFS
     * @return true if successful, false otherwise
     */
    public boolean deleteFile(String dfsFilePath) {
        logger.info("Deleting file: {}", dfsFilePath);

        try {
            Message deleteRequest = new Message(Message.MessageType.DELETE_REQUEST);
            deleteRequest.put("filePath", dfsFilePath);

            Message response = NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, deleteRequest);

            if (Boolean.TRUE.equals(response.get("success"))) {
                logger.info("File deleted successfully: {}", dfsFilePath);
                return true;
            } else {
                logger.error("Delete request failed: {}", response.get("message"));
                return false;
            }

        } catch (Exception e) {
            logger.error("Error deleting file", e);
            return false;
        }
    }

    /**
     * List files in a directory.
     *
     * @param directory Directory path in DFS
     * @return List of file metadata
     */
    public List<FileMetadata> listFiles(String directory) {
        logger.info("Listing files in directory: {}", directory);

        try {
            Message listRequest = new Message(Message.MessageType.LIST_REQUEST);
            listRequest.put("directory", directory);

            Message response = NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, listRequest);

            if (Boolean.TRUE.equals(response.get("success"))) {
                @SuppressWarnings("unchecked")
                List<FileMetadata> files = (List<FileMetadata>) response.get("files");
                logger.info("Found {} files", files.size());
                return files;
            } else {
                logger.error("List request failed");
                return Collections.emptyList();
            }

        } catch (Exception e) {
            logger.error("Error listing files", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get file information.
     *
     * @param dfsFilePath Path in DFS
     * @return FileMetadata or null if not found
     */
    public FileMetadata getFileInfo(String dfsFilePath) {
        logger.info("Getting file info: {}", dfsFilePath);

        try {
            Message request = new Message(Message.MessageType.GET_FILE_INFO);
            request.put("filePath", dfsFilePath);

            Message response = NetworkUtils.sendAndReceive(nameNodeHost, nameNodePort, request);

            if (Boolean.TRUE.equals(response.get("success"))) {
                return (FileMetadata) response.get("metadata");
            } else {
                logger.error("Get file info failed: {}", response.get("message"));
                return null;
            }

        } catch (Exception e) {
            logger.error("Error getting file info", e);
            return null;
        }
    }

    private byte[] readLocalFile(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return fis.readAllBytes();
        }
    }

    private boolean writeBlockToDataNodes(String blockId, byte[] blockData, List<DataNodeInfo> dataNodes) {
        // Write to all replicas
        for (DataNodeInfo dataNode : dataNodes) {
            try {
                if (!writeBlockToDataNode(blockId, blockData, dataNode)) {
                    logger.error("Failed to write block to DataNode: {}", dataNode.getAddress());
                    return false;
                }
            } catch (Exception e) {
                logger.error("Error writing block to DataNode: {}", dataNode.getAddress(), e);
                return false;
            }
        }
        return true;
    }

    private boolean writeBlockToDataNode(String blockId, byte[] blockData, DataNodeInfo dataNode) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(dataNode.getHost(), dataNode.getPort())) {
            // Send write block request
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            Message writeRequest = new Message(Message.MessageType.WRITE_BLOCK);
            writeRequest.put("blockId", blockId);
            writeRequest.put("blockSize", (long) blockData.length);
            out.writeObject(writeRequest);
            out.flush();

            // Send block data
            OutputStream dataOut = socket.getOutputStream();
            dataOut.write(blockData);
            dataOut.flush();

            // Wait for acknowledgment
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Message response = (Message) in.readObject();

            return Boolean.TRUE.equals(response.get("success"));
        }
    }

    private byte[] readBlockFromDataNodes(String blockId, List<DataNodeInfo> dataNodes) {
        // Try each DataNode until success
        for (DataNodeInfo dataNode : dataNodes) {
            try {
                byte[] blockData = readBlockFromDataNode(blockId, dataNode);
                if (blockData != null) {
                    return blockData;
                }
            } catch (Exception e) {
                logger.warn("Failed to read block from DataNode: {}", dataNode.getAddress(), e);
            }
        }
        return null;
    }

    private byte[] readBlockFromDataNode(String blockId, DataNodeInfo dataNode) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(dataNode.getHost(), dataNode.getPort())) {
            // Send read block request
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            Message readRequest = new Message(Message.MessageType.READ_BLOCK);
            readRequest.put("blockId", blockId);
            out.writeObject(readRequest);
            out.flush();

            // Receive response
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Message response = (Message) in.readObject();

            if (!Boolean.TRUE.equals(response.get("success"))) {
                logger.error("Read block failed: {}", response.get("message"));
                return null;
            }

            // Receive block data
            int size = ((Number) response.get("size")).intValue();
            byte[] blockData = new byte[size];
            InputStream dataIn = socket.getInputStream();

            int totalRead = 0;
            while (totalRead < size) {
                int read = dataIn.read(blockData, totalRead, size - totalRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of stream");
                }
                totalRead += read;
            }

            // Verify checksum
            String expectedChecksum = (String) response.get("checksum");
            if (!NetworkUtils.verifyChecksum(blockData, expectedChecksum)) {
                logger.error("Checksum verification failed for block: {}", blockId);
                return null;
            }

            return blockData;
        }
    }
}
