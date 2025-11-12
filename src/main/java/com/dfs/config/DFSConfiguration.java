package com.dfs.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration management for the distributed file system.
 */
public class DFSConfiguration {
    private static final String DEFAULT_CONFIG_FILE = "dfs.properties";

    // Default values
    public static final long DEFAULT_BLOCK_SIZE = 128 * 1024 * 1024; // 128 MB
    public static final int DEFAULT_REPLICATION_FACTOR = 3;
    public static final int DEFAULT_NAMENODE_PORT = 9000;
    public static final int DEFAULT_DATANODE_PORT = 9001;
    public static final long DEFAULT_HEARTBEAT_INTERVAL = 3000; // 3 seconds
    public static final long DEFAULT_HEARTBEAT_TIMEOUT = 10000; // 10 seconds
    public static final String DEFAULT_DATA_DIR = "./dfs-data";
    public static final String DEFAULT_NAMENODE_HOST = "localhost";

    private final Properties properties;

    public DFSConfiguration() {
        this.properties = new Properties();
        loadDefaults();
    }

    public DFSConfiguration(String configFile) throws IOException {
        this.properties = new Properties();
        loadDefaults();
        loadFromFile(configFile);
    }

    private void loadDefaults() {
        properties.setProperty("dfs.block.size", String.valueOf(DEFAULT_BLOCK_SIZE));
        properties.setProperty("dfs.replication.factor", String.valueOf(DEFAULT_REPLICATION_FACTOR));
        properties.setProperty("dfs.namenode.port", String.valueOf(DEFAULT_NAMENODE_PORT));
        properties.setProperty("dfs.datanode.port", String.valueOf(DEFAULT_DATANODE_PORT));
        properties.setProperty("dfs.heartbeat.interval", String.valueOf(DEFAULT_HEARTBEAT_INTERVAL));
        properties.setProperty("dfs.heartbeat.timeout", String.valueOf(DEFAULT_HEARTBEAT_TIMEOUT));
        properties.setProperty("dfs.data.dir", DEFAULT_DATA_DIR);
        properties.setProperty("dfs.namenode.host", DEFAULT_NAMENODE_HOST);
    }

    private void loadFromFile(String configFile) throws IOException {
        try (InputStream input = new FileInputStream(configFile)) {
            properties.load(input);
        }
    }

    public long getBlockSize() {
        return Long.parseLong(properties.getProperty("dfs.block.size"));
    }

    public int getReplicationFactor() {
        return Integer.parseInt(properties.getProperty("dfs.replication.factor"));
    }

    public int getNameNodePort() {
        return Integer.parseInt(properties.getProperty("dfs.namenode.port"));
    }

    public int getDataNodePort() {
        return Integer.parseInt(properties.getProperty("dfs.datanode.port"));
    }

    public long getHeartbeatInterval() {
        return Long.parseLong(properties.getProperty("dfs.heartbeat.interval"));
    }

    public long getHeartbeatTimeout() {
        return Long.parseLong(properties.getProperty("dfs.heartbeat.timeout"));
    }

    public String getDataDir() {
        return properties.getProperty("dfs.data.dir");
    }

    public String getNameNodeHost() {
        return properties.getProperty("dfs.namenode.host");
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
