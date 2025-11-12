package com.dfs;

import com.dfs.client.DFSClient;
import com.dfs.common.FileMetadata;
import com.dfs.config.DFSConfiguration;
import com.dfs.datanode.DataNode;
import com.dfs.namenode.NameNode;

import java.util.List;
import java.util.Scanner;

/**
 * Main entry point for the Distributed File System.
 */
public class Main {
    private static final String USAGE = """
            Distributed File System (DFS) - Usage:

            Start NameNode:
              java -cp target/distributed-file-storage-1.0.0.jar com.dfs.namenode.NameNode [port]

            Start DataNode:
              java -cp target/distributed-file-storage-1.0.0.jar com.dfs.datanode.DataNode [port]

            Client Operations:
              java -cp target/distributed-file-storage-1.0.0.jar com.dfs.Main client

            Or use the interactive mode:
              java -jar target/distributed-file-storage-1.0.0.jar
            """;

    public static void main(String[] args) {
        if (args.length == 0) {
            runInteractiveMode();
        } else {
            String command = args[0].toLowerCase();
            switch (command) {
                case "namenode" -> startNameNode(args);
                case "datanode" -> startDataNode(args);
                case "client" -> runClientMode();
                case "help", "-h", "--help" -> System.out.println(USAGE);
                default -> {
                    System.out.println("Unknown command: " + command);
                    System.out.println(USAGE);
                }
            }
        }
    }

    private static void startNameNode(String[] args) {
        try {
            DFSConfiguration config = new DFSConfiguration();
            if (args.length > 1) {
                config.setProperty("dfs.namenode.port", args[1]);
            }

            System.out.println("Starting NameNode on port " + config.getNameNodePort());
            NameNode nameNode = new NameNode(config);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down NameNode...");
                nameNode.stop();
            }));

            nameNode.start();
        } catch (Exception e) {
            System.err.println("Failed to start NameNode: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void startDataNode(String[] args) {
        try {
            DFSConfiguration config = new DFSConfiguration();
            int port = config.getDataNodePort();

            if (args.length > 1) {
                port = Integer.parseInt(args[1]);
            }

            System.out.println("Starting DataNode on port " + port);
            DataNode dataNode = new DataNode(config, port);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down DataNode...");
                dataNode.stop();
            }));

            dataNode.start();
        } catch (Exception e) {
            System.err.println("Failed to start DataNode: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runClientMode() {
        DFSConfiguration config = new DFSConfiguration();
        DFSClient client = new DFSClient(config);
        Scanner scanner = new Scanner(System.in);

        System.out.println("DFS Client Interactive Mode");
        System.out.println("Commands: put, get, delete, list, info, exit");

        while (true) {
            System.out.print("\ndfs> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            switch (command) {
                case "put" -> {
                    if (parts.length < 3) {
                        System.out.println("Usage: put <local-file> <dfs-path>");
                        continue;
                    }
                    String localFile = parts[1];
                    String dfsPath = parts[2];
                    System.out.println("Writing file: " + localFile + " -> " + dfsPath);
                    boolean success = client.writeFile(localFile, dfsPath);
                    System.out.println(success ? "Success!" : "Failed!");
                }
                case "get" -> {
                    if (parts.length < 3) {
                        System.out.println("Usage: get <dfs-path> <local-file>");
                        continue;
                    }
                    String dfsPath = parts[1];
                    String localFile = parts[2];
                    System.out.println("Reading file: " + dfsPath + " -> " + localFile);
                    boolean success = client.readFile(dfsPath, localFile);
                    System.out.println(success ? "Success!" : "Failed!");
                }
                case "delete", "rm" -> {
                    if (parts.length < 2) {
                        System.out.println("Usage: delete <dfs-path>");
                        continue;
                    }
                    String dfsPath = parts[1];
                    System.out.println("Deleting file: " + dfsPath);
                    boolean success = client.deleteFile(dfsPath);
                    System.out.println(success ? "Success!" : "Failed!");
                }
                case "list", "ls" -> {
                    String directory = parts.length > 1 ? parts[1] : "/";
                    System.out.println("Listing files in: " + directory);
                    List<FileMetadata> files = client.listFiles(directory);
                    if (files.isEmpty()) {
                        System.out.println("No files found");
                    } else {
                        System.out.printf("%-50s %15s %10s%n", "Path", "Size", "Blocks");
                        System.out.println("-".repeat(77));
                        for (FileMetadata file : files) {
                            System.out.printf("%-50s %15d %10d%n",
                                    file.getFilePath(),
                                    file.getFileSize(),
                                    file.getBlockIds().size());
                        }
                    }
                }
                case "info" -> {
                    if (parts.length < 2) {
                        System.out.println("Usage: info <dfs-path>");
                        continue;
                    }
                    String dfsPath = parts[1];
                    FileMetadata metadata = client.getFileInfo(dfsPath);
                    if (metadata != null) {
                        System.out.println("File: " + metadata.getFilePath());
                        System.out.println("Size: " + metadata.getFileSize() + " bytes");
                        System.out.println("Blocks: " + metadata.getBlockIds().size());
                        System.out.println("Replication: " + metadata.getReplicationFactor());
                        System.out.println("Created: " + new java.util.Date(metadata.getCreationTime()));
                    } else {
                        System.out.println("File not found");
                    }
                }
                case "exit", "quit" -> {
                    System.out.println("Goodbye!");
                    return;
                }
                case "help" -> {
                    System.out.println("Available commands:");
                    System.out.println("  put <local-file> <dfs-path>  - Upload file to DFS");
                    System.out.println("  get <dfs-path> <local-file>  - Download file from DFS");
                    System.out.println("  delete <dfs-path>            - Delete file from DFS");
                    System.out.println("  list [directory]             - List files in directory");
                    System.out.println("  info <dfs-path>              - Show file information");
                    System.out.println("  exit                         - Exit client");
                }
                default -> System.out.println("Unknown command: " + command + " (type 'help' for help)");
            }
        }
    }

    private static void runInteractiveMode() {
        System.out.println(USAGE);
        System.out.println("\nSelect mode:");
        System.out.println("1. Start NameNode");
        System.out.println("2. Start DataNode");
        System.out.println("3. Client Mode");
        System.out.println("4. Exit");

        Scanner scanner = new Scanner(System.in);
        System.out.print("\nChoice: ");

        if (scanner.hasNextInt()) {
            int choice = scanner.nextInt();
            switch (choice) {
                case 1 -> startNameNode(new String[]{});
                case 2 -> startDataNode(new String[]{});
                case 3 -> runClientMode();
                case 4 -> System.out.println("Goodbye!");
                default -> System.out.println("Invalid choice");
            }
        } else {
            System.out.println("Invalid input");
        }
    }
}
