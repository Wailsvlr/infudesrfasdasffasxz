#!/usr/bin/env python3
"""
Creates LimpClient.exe - a single Windows executable that:
1. Extracts the embedded LIMP Client JAR to %TEMP%
2. Launches it with javaw (no console window)
3. No installation needed, just double-click

Uses a .cmd stub with PowerShell extraction. The .cmd extension
is renamed to .exe but Windows still executes the batch commands.
For a truly silent launch, we also create a .vbs wrapper.
"""
import sys
import os
import base64

def create_exe(jar_path, exe_path):
    with open(jar_path, 'rb') as f:
        jar_data = f.read()

    # Base64 encode the JAR so it survives batch file parsing
    jar_b64 = base64.b64encode(jar_data).decode('ascii')

    # Split base64 into chunks for echo statements (max ~8000 chars per line)
    chunk_size = 8000
    chunks = [jar_b64[i:i+chunk_size] for i in range(0, len(jar_b64), chunk_size)]

    # Build the batch script
    lines = []
    lines.append('@echo off')
    lines.append('setlocal EnableDelayedExpansion')
    lines.append('title LIMP Client')
    lines.append('')
    lines.append('set "TDIR=%TEMP%\\LimpClient"')
    lines.append('if not exist "%TDIR%" mkdir "%TDIR%"')
    lines.append('set "JAR=%TDIR%\\LimpClient.jar"')
    lines.append('set "B64=%TDIR%\\limp.b64"')
    lines.append('')
    lines.append('REM Write base64 data')
    lines.append('(')
    for i, chunk in enumerate(chunks):
        lines.append(f'echo {chunk}')
    lines.append(') > "%B64%"')
    lines.append('')
    lines.append('REM Decode base64 to JAR')
    lines.append('certutil -decode "%B64%" "%JAR%" >nul 2>&1')
    lines.append('del "%B64%" >nul 2>&1')
    lines.append('')
    lines.append('if not exist "%JAR%" (')
    lines.append('    echo LIMP Client: extraction failed')
    lines.append('    pause')
    lines.append('    exit /b 1')
    lines.append(')')
    lines.append('')
    lines.append('REM Launch with javaw (no console)')
    lines.append('where javaw >nul 2>&1 && (start "" javaw -jar "%JAR%" & exit /b 0)')
    lines.append('where java >nul 2>&1 && (start "" java -jar "%JAR%" & exit /b 0)')
    lines.append('if exist "%JAVA_HOME%\\bin\\javaw.exe" (start "" "%JAVA_HOME%\\bin\\javaw.exe" -jar "%JAR%" & exit /b 0)')
    lines.append('')
    lines.append('REM Search common Java paths')
    lines.append('for %%d in ("%ProgramFiles%\\Java" "%ProgramFiles%\\Eclipse Adoptium" "%LocalAppData%\\Programs\\Eclipse Adoptium") do (')
    lines.append('    if exist %%d (')
    lines.append('        for /d %%j in (%%d\\*) do (')
    lines.append('            if exist "%%j\\bin\\javaw.exe" (start "" "%%j\\bin\\javaw.exe" -jar "%JAR%" & exit /b 0)')
    lines.append('        )')
    lines.append('    )')
    lines.append(')')
    lines.append('')
    lines.append('echo.')
    lines.append('echo  LIMP Client - Java 17+ Required')
    lines.append('echo  Download: https://adoptium.net')
    lines.append('echo.')
    lines.append('pause')

    script = '\r\n'.join(lines)

    with open(exe_path, 'w', newline='') as f:
        f.write(script)

    # Also create a .vbs launcher that runs the .exe silently (no console flash)
    vbs_path = exe_path.replace('.exe', '.vbs')
    vbs = f'''Set WshShell = CreateObject("WScript.Shell")
WshShell.Run Chr(34) & Replace(WScript.ScriptFullName, ".vbs", ".exe") & Chr(34), 0, False
'''
    with open(vbs_path, 'w') as f:
        f.write(vbs)

    sz = os.path.getsize(exe_path)
    print(f"Created {exe_path} ({sz:,} bytes / {sz/1024:.0f} KB)")
    print(f"Created {vbs_path} (silent launcher)")
    print(f"  Embedded JAR: {len(jar_data):,} bytes")
    print(f"  Base64 chunks: {len(chunks)}")

if __name__ == '__main__':
    jar = sys.argv[1] if len(sys.argv) > 1 else 'LimpInjector.jar'
    exe = sys.argv[2] if len(sys.argv) > 2 else 'LimpClient.exe'
    create_exe(jar, exe)
