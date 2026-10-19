@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 goto failed
start "" javaw -cp "out;lib/*" campus.booking.Main
goto :eof
:failed
echo.
echo Could not build the application. Fix the compilation errors and try again.
pause
endlocal
