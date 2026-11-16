@echo off
chcp 65001 >nul
title DeepSetk - IA local
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-ia.ps1"
pause
