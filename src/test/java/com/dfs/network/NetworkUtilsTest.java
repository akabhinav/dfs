package com.dfs.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkUtilsTest {

    @Test
    void testCalculateChecksum() {
        byte[] data = "Hello, World!".getBytes();
        String checksum = NetworkUtils.calculateChecksum(data);

        assertNotNull(checksum);
        assertEquals(16, checksum.length()); // 16 hex characters
    }

    @Test
    void testVerifyChecksum() {
        byte[] data = "Test data for checksum".getBytes();
        String checksum = NetworkUtils.calculateChecksum(data);

        assertTrue(NetworkUtils.verifyChecksum(data, checksum));

        // Modify data
        data[0] = (byte) (data[0] + 1);
        assertFalse(NetworkUtils.verifyChecksum(data, checksum));
    }

    @Test
    void testChecksumConsistency() {
        byte[] data = "Consistency test".getBytes();
        String checksum1 = NetworkUtils.calculateChecksum(data);
        String checksum2 = NetworkUtils.calculateChecksum(data);

        assertEquals(checksum1, checksum2);
    }
}
