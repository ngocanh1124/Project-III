@echo off
REM Face Recognition AI Server Startup Script for Windows

echo ========================================
echo Face Recognition AI HTTP Server
echo ========================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.8+ and add it to PATH
    pause
    exit /b 1
)

REM Check if virtual environment exists
if not exist "venv" (
    echo Creating virtual environment...
    python -m venv venv
    if errorlevel 1 (
        echo ERROR: Failed to create virtual environment
        pause
        exit /b 1
    )
    echo.
)

REM Activate virtual environment
echo Activating virtual environment...
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo ERROR: Failed to activate virtual environment
    pause
    exit /b 1
)
echo ✓ Virtual environment activated

REM Install/upgrade dependencies
echo.
echo Installing dependencies...
pip install -q -r requirements_ai.txt
if errorlevel 1 (
    echo ERROR: Failed to install dependencies
    pause
    exit /b 1
)
echo ✓ Dependencies installed

REM Check for GPU support
echo.
echo Checking for GPU support...
python -c "import onnxruntime; print('Available providers:', onnxruntime.get_available_providers())"

REM Start server
echo.
echo ========================================
echo Starting Face Recognition AI Server...
echo ========================================
echo Server will run on http://localhost:5000
echo Press Ctrl+C to stop
echo.
echo Available Endpoints:
echo   GET  /health
echo   POST /api/v1/face/compare
echo   POST /api/v1/face/compare-many
echo   POST /api/v1/face/extract
echo   GET  /api/v1/status
echo.
echo ========================================
echo.

python face_engine_server.py

REM If script exits here, keep window open
pause
