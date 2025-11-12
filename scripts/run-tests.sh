#!/bin/bash

# Run all tests

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "Running DFS Tests..."
echo ""

mvn clean test

echo ""
echo "Tests completed. Check the output above for results."
