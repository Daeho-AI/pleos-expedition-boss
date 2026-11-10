@echo off
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start_social.ps1"
if errorlevel 1 (
 pause
 exit /b 1
)
exit /b 0
