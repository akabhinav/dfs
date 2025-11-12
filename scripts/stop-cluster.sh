#!/bin/bash

# Stop the DFS cluster

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_DIR/logs"

echo "Stopping DFS Cluster..."

# Stop NameNode
if [ -f "$LOG_DIR/namenode.pid" ]; then
    NAMENODE_PID=$(cat "$LOG_DIR/namenode.pid")
    if kill -0 $NAMENODE_PID 2>/dev/null; then
        echo "Stopping NameNode (PID: $NAMENODE_PID)..."
        kill $NAMENODE_PID
        rm "$LOG_DIR/namenode.pid"
    else
        echo "NameNode is not running"
        rm "$LOG_DIR/namenode.pid"
    fi
else
    echo "NameNode PID file not found"
fi

# Stop DataNodes
for port in 9001 9002 9003; do
    if [ -f "$LOG_DIR/datanode-$port.pid" ]; then
        DATANODE_PID=$(cat "$LOG_DIR/datanode-$port.pid")
        if kill -0 $DATANODE_PID 2>/dev/null; then
            echo "Stopping DataNode on port $port (PID: $DATANODE_PID)..."
            kill $DATANODE_PID
            rm "$LOG_DIR/datanode-$port.pid"
        else
            echo "DataNode on port $port is not running"
            rm "$LOG_DIR/datanode-$port.pid"
        fi
    fi
done

echo "DFS Cluster stopped"
