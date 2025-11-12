package com.dfs.network;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic message for network communication between nodes.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        // Client to NameNode
        WRITE_REQUEST,
        READ_REQUEST,
        DELETE_REQUEST,
        LIST_REQUEST,
        GET_FILE_INFO,

        // NameNode to Client
        WRITE_RESPONSE,
        READ_RESPONSE,
        DELETE_RESPONSE,
        LIST_RESPONSE,
        FILE_INFO_RESPONSE,

        // DataNode to NameNode
        REGISTER_DATANODE,
        HEARTBEAT,
        BLOCK_REPORT,

        // NameNode to DataNode
        REGISTER_ACK,
        REPLICATE_BLOCK,
        DELETE_BLOCK,

        // Client to DataNode
        WRITE_BLOCK,
        READ_BLOCK,

        // DataNode to Client
        BLOCK_DATA,
        BLOCK_ACK,

        // Generic
        SUCCESS,
        ERROR
    }

    private final MessageType type;
    private final Map<String, Object> data;
    private final long timestamp;

    public Message(MessageType type) {
        this.type = type;
        this.data = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public Message(MessageType type, Map<String, Object> data) {
        this.type = type;
        this.data = new HashMap<>(data);
        this.timestamp = System.currentTimeMillis();
    }

    public MessageType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", dataKeys=" + data.keySet() +
                ", timestamp=" + timestamp +
                '}';
    }
}
