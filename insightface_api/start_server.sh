#!/bin/bash
# Face Recognition AI Server Startup Script for Linux/Mac

echo "========================================"
echo "Face Recognition AI HTTP Server"
echo "========================================"
echo ""

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python 3 is not installed"
    echo "Please install Python 3.8+ first"
    exit 1
fi

python3 --version

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to create virtual environment"
        exit 1
    fi
    echo "✓ Virtual environment created"
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to activate virtual environment"
    exit 1
fi
echo "✓ Virtual environment activated"

# Install/upgrade dependencies
echo ""
echo "Installing dependencies..."
pip install -q -r requirements_ai.txt
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to install dependencies"
    exit 1
fi
echo "✓ Dependencies installed"

# Check for GPU support
echo ""
echo "Checking for GPU support..."
python3 -c "import onnxruntime; print('Available providers:', onnxruntime.get_available_providers())"

# Start server
echo ""
echo "========================================"
echo "Starting Face Recognition AI Server..."
echo "========================================"
echo "Server will run on http://localhost:5000"
echo "Press Ctrl+C to stop"
echo ""
echo "Available Endpoints:"
echo "  GET  /health"
echo "  POST /api/v1/face/compare"
echo "  POST /api/v1/face/compare-many"
echo "  POST /api/v1/face/extract"
echo "  GET  /api/v1/status"
echo ""
echo "========================================"
echo ""

python3 face_engine_server.py
