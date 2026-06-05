#!/bin/bash

echo "=========================================="
echo "Stopping Automation Quality Dashboard"
echo "=========================================="
echo ""

# Stop services
echo "Stopping services..."
docker-compose down

echo ""
echo "Services stopped successfully!"
echo ""
echo "To remove all data (including database):"
echo "  docker-compose down -v"
echo ""
echo "To start again:"
echo "  ./start.sh"
