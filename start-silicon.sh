#!/bin/bash

# Print start message
echo "Starting build and deployment for Apple Silicon (M1/M2/M3)..."

# Build and start the container with build logs
docker-compose build --no-cache app-silicon && \
docker-compose up -d app-silicon

# Wait for a moment to let the container start
sleep 5

# Check container logs
echo "Checking container logs..."
docker-compose logs app-silicon

# Check if the container is running
if docker-compose ps | grep -q "app-silicon.*running"; then
    echo "✅ Application successfully started!"
    echo "🌐 You can access the application at: http://localhost:8080"
else
    echo "❌ Container failed to start properly. Showing detailed logs:"
    docker-compose logs app-silicon
    echo "Try running: docker-compose build --no-cache app-silicon to see detailed build logs"
fi 