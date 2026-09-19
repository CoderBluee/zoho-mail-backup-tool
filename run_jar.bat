@echo off
cd /d "%~dp0"
title Prism IMAP Backup Tool
echo ===================================================
echo   Launching Prism IMAP Backup Tool (Windows JAR)
echo ===================================================
echo.
java -Xmx4g -XX:+UseG1GC --enable-native-access=ALL-UNNAMED -jar "target\imap-backup-tool-1.0-SNAPSHOT.jar"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application exited with error code %ERRORLEVEL%.
    pause
)
