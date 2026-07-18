@echo off
REM Windows Batch Script - Start Face Recognition Server (InsightFace + FaceNet fallback)

setlocal enabledelayedexpansion

echo.
echo ============================================================
echo   Face Recognition HTTP Server (InsightFace buffalo_sc)
echo ============================================================
echo.

REM Navigate to script directory
cd /d "%~dp0"

REM Activate virtual environment
if exist ".venv\Scripts\activate.bat" (
    echo [OK] Activating .venv...
    call .venv\Scripts\activate.bat
) else (
    echo [WARNING] .venv not found, using system Python
)

REM Suppress albumentations update warning
set NO_ALBUMENTATIONS_UPDATE=1

REM Verify server file
if not exist "face_engine_server_no_cpp.py" (
    echo [ERROR] face_engine_server_no_cpp.py not found
    pause
    exit /b 1
)

echo [OK] Starting server...
echo.

python face_engine_server_no_cpp.py

if errorlevel 1 (
    echo.
    echo [ERROR] Server exited with error
    pause
    exit /b 1
)

exit /b 0
