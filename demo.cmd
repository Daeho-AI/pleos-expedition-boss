@echo off
chcp 65001 >nul
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\run_demo.ps1" -Mode fixture
if errorlevel 1 (
    pause
    exit /b 1
)
exit /b 0
