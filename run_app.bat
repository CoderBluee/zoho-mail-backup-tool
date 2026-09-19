@echo off
cd /d "%~dp0"
title Prism IMAP Backup Tool Runner
echo ===================================================
echo   Launching Prism IMAP Backup Tool via Maven JavaFX
echo ===================================================
echo.
call mvn javafx:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application stopped with error code %ERRORLEVEL%.
    pause
)
