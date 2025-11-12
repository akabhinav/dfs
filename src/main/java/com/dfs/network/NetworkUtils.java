package com.dfs.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

/**
 * Utility class for network operations.
 */
public class NetworkUtils {
    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    /**
     * Send a message over a socket connection.
     */
    public static void sendMessage(Socket socket, Message message) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject(message);
            out.flush();
        }
    }

    /**
     * Receive a message from a socket connection.
     */
    public static Message receiveMessage(Socket socket) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            return (Message) in.readObject();
        }
    }

    /**
     * Send raw bytes over a socket connection.
     */
    public static void sendBytes(Socket socket, byte[] data) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    /**
     * Receive raw bytes from a socket connection.
     */
    public static byte[] receiveBytes(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    /**
     * Send a message and wait for response.
     */
    public static Message sendAndReceive(String host, int port, Message message) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(message);
            out.flush();

            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            return (Message) in.readObject();
        }
    }

    /**
     * Calculate checksum for data.
     */
    public static String calculateChecksum(byte[] data) {
        long checksum = 0;
        for (byte b : data) {
            checksum += (b & 0xFF);
        }
        return String.format("%016x", checksum);
    }

    /**
     * Verify checksum for data.
     */
    public static boolean verifyChecksum(byte[] data, String expectedChecksum) {
        String actualChecksum = calculateChecksum(data);
        return actualChecksum.equals(expectedChecksum);
    }
}
