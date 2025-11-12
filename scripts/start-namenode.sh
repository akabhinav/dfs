#!/bin/bash

# Start NameNode

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_FILE="$PROJECT_DIR/target/distributed-file-storage-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please build the project first: mvn clean package"
    exit 1
fi

NAMENODE_PORT="${1:-9000}"

echo "Starting NameNode on port $NAMENODE_PORT..."

java -cp "$JAR_FILE" com.dfs.namenode.NameNode "$NAMENODE_PORT"
