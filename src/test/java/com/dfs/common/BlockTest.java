package com.dfs.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockTest {

    @Test
    void testBlockCreation() {
        Block block = Block.create(1024, "checksum123");

        assertNotNull(block.getBlockId());
        assertEquals(1024, block.getBlockSize());
        assertEquals("checksum123", block.getChecksum());
        assertTrue(block.getTimestamp() > 0);
    }

    @Test
    void testBlockEquality() {
        Block block1 = new Block("block-123", 1024, "checksum");
        Block block2 = new Block("block-123", 2048, "checksum2");
        Block block3 = new Block("block-456", 1024, "checksum");

        assertEquals(block1, block2); // Same ID
        assertNotEquals(block1, block3); // Different ID
    }

    @Test
    void testBlockHashCode() {
        Block block1 = new Block("block-123", 1024, "checksum");
        Block block2 = new Block("block-123", 2048, "checksum2");

        assertEquals(block1.hashCode(), block2.hashCode());
    }
}
