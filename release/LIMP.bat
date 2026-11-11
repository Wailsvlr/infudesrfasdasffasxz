@echo off
title LIMP Client
where javaw >nul 2>&1 && (start "" javaw -jar "%~dp0LimpInjector.jar" & exit /b 0)
where java >nul 2>&1 && (start "" java -jar "%~dp0LimpInjector.jar" & exit /b 0)
if exist "%JAVA_HOME%\bin\javaw.exe" (start "" "%JAVA_HOME%\bin\javaw.exe" -jar "%~dp0LimpInjector.jar" & exit /b 0)
for %%d in ("%ProgramFiles%\Java" "%ProgramFiles%\Eclipse Adoptium" "%LocalAppData%\Programs\Eclipse Adoptium") do (
    if exist %%d for /d %%j in (%%d\*) do if exist "%%j\bin\javaw.exe" (start "" "%%j\bin\javaw.exe" -jar "%~dp0LimpInjector.jar" & exit /b 0)
)
echo.
echo  Java 17+ gerekli. Indir: https://adoptium.net
echo.
pause
