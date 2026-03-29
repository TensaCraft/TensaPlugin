@echo off
where pwsh >nul 2>&1
if %errorlevel%==0 (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-velocity.ps1" %*
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-velocity.ps1" %*
)
