@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
if not exist lib\sqlite-jdbc-3.53.4.0.jar (
  echo Missing lib\sqlite-jdbc-3.53.4.0.jar. Restore the bundled SQLite JDBC driver.
  goto failed
)
if not exist out mkdir out
for /r out %%f in (*.class) do del /q "%%f"
set "SOURCES="
for /r src\main\java %%f in (*.java) do set SOURCES=!SOURCES! "%%f"
for /r src\test\java %%f in (*.java) do set SOURCES=!SOURCES! "%%f"
if not defined SOURCES (
  echo No Java source files found.
  goto failed
)
echo Compiling application and tests...
javac -encoding UTF-8 -cp "lib\*" -d out !SOURCES!
if errorlevel 1 goto failed
echo Running booking service checks...
java -cp "out;lib/*" campus.booking.BookingServiceTest
if errorlevel 1 goto failed
echo.
echo All checks passed.
goto end
:failed
echo.
echo Tests failed. Review the error messages above.
endlocal & exit /b 1
:end
echo.
endlocal & exit /b 0
