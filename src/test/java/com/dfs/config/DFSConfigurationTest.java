package com.dfs.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DFSConfigurationTest {

    @Test
    void testDefaultConfiguration() {
        DFSConfiguration config = new DFSConfiguration();

        assertEquals(DFSConfiguration.DEFAULT_BLOCK_SIZE, config.getBlockSize());
        assertEquals(DFSConfiguration.DEFAULT_REPLICATION_FACTOR, config.getReplicationFactor());
        assertEquals(DFSConfiguration.DEFAULT_NAMENODE_PORT, config.getNameNodePort());
        assertEquals(DFSConfiguration.DEFAULT_DATANODE_PORT, config.getDataNodePort());
        assertEquals(DFSConfiguration.DEFAULT_HEARTBEAT_INTERVAL, config.getHeartbeatInterval());
        assertEquals(DFSConfiguration.DEFAULT_HEARTBEAT_TIMEOUT, config.getHeartbeatTimeout());
    }

    @Test
    void testSetProperty() {
        DFSConfiguration config = new DFSConfiguration();

        config.setProperty("dfs.block.size", "67108864"); // 64 MB
        assertEquals(67108864, config.getBlockSize());

        config.setProperty("dfs.replication.factor", "5");
        assertEquals(5, config.getReplicationFactor());
    }

    @Test
    void testGetProperty() {
        DFSConfiguration config = new DFSConfiguration();

        String blockSize = config.getProperty("dfs.block.size");
        assertNotNull(blockSize);
        assertEquals(String.valueOf(DFSConfiguration.DEFAULT_BLOCK_SIZE), blockSize);

        String customProp = config.getProperty("custom.property", "default-value");
        assertEquals("default-value", customProp);
    }
}
