#!/bin/bash

# Start a local DFS cluster with 1 NameNode and 3 DataNodes

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_FILE="$PROJECT_DIR/target/distributed-file-storage-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please build the project first: mvn clean package"
    exit 1
fi

LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

echo "Starting DFS Cluster..."

# Start NameNode
echo "Starting NameNode on port 9000..."
nohup java -cp "$JAR_FILE" com.dfs.namenode.NameNode 9000 > "$LOG_DIR/namenode.log" 2>&1 &
NAMENODE_PID=$!
echo $NAMENODE_PID > "$LOG_DIR/namenode.pid"
echo "NameNode started (PID: $NAMENODE_PID)"

# Wait for NameNode to start
sleep 3

# Start DataNodes
for port in 9001 9002 9003; do
    echo "Starting DataNode on port $port..."
    nohup java -cp "$JAR_FILE" com.dfs.datanode.DataNode $port > "$LOG_DIR/datanode-$port.log" 2>&1 &
    DATANODE_PID=$!
    echo $DATANODE_PID > "$LOG_DIR/datanode-$port.pid"
    echo "DataNode started on port $port (PID: $DATANODE_PID)"
    sleep 1
done

echo ""
echo "DFS Cluster started successfully!"
echo "NameNode: localhost:9000"
echo "DataNodes: localhost:9001, localhost:9002, localhost:9003"
echo ""
echo "Logs are in: $LOG_DIR"
echo "To stop the cluster: ./scripts/stop-cluster.sh"
echo "To run client: ./scripts/run-client.sh"
