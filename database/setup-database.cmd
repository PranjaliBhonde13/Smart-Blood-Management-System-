@echo off
rem Double-click or run from cmd. Extra arguments are passed through, e.g.:
rem   setup-database.cmd -Port 3309 -AdminPassword secret
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-database.ps1" %*
pause
