@echo off
title LIMP Client Launcher
javaw -jar "%~dp0LimpInjector.jar"
if errorlevel 1 (
    echo Java not found! Install Java 17+ from https://adoptium.net
    pause
)
