#!/bin/bash

# Print stop message
echo "Stopping Apple Silicon (M1/M2/M3) containers..."

# Stop the container
docker-compose stop app-silicon

# Remove the container
docker-compose rm -f app-silicon

echo "✅ Application successfully stopped and cleaned up!" 