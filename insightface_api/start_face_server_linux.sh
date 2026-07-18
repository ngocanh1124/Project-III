#!/bin/bash
# Linux/Mac Bash Script - Start Face Recognition Server (Windows Compatible Version)

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo ""
echo "============================================================"
echo "  Face Recognition HTTP Server - FaceNet Edition"
echo "============================================================"
echo ""

# Check if Python is available
if ! command - v python3 &> /dev/null; then
    echo -e "${RED}[ERROR]${NC} Python3 not found"
    exit 1
fi

# Navigate to script directory
cd "$(dirname "$0")"

# Verify required files
if [ ! -f "face_engine_server_no_cpp.py" ]; then
    echo -e "${RED}[ERROR]${NC} face_engine_server_no_cpp.py not found"
    exit 1
fi

echo -e "${GREEN}[OK]${NC} All files present"
echo ""

# Check if dependencies are installed
echo "Checking dependencies..."
if ! python3 -c "import facenet_pytorch; import torch; import flask; import cv2" 2>/dev/null; then
    echo -e "${YELLOW}[WARNING]${NC} Some dependencies missing. Installing..."
    pip install facenet-pytorch torch pillow opencv-python scipy flask flask-cors -q
    echo -e "${GREEN}[OK]${NC} Dependencies installed"
else
    echo -e "${GREEN}[OK]${NC} All dependencies present"
fi

echo ""
echo "============================================================"
echo "Starting Face Recognition Server..."
echo "============================================================"
echo ""

python3 face_engine_server_no_cpp.py
