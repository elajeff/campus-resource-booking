@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
if not exist lib\sqlite-jdbc-3.53.4.0.jar (
  echo Missing lib\sqlite-jdbc-3.53.4.0.jar. Restore the bundled SQLite JDBC driver.
  exit /b 1
)
if not exist out mkdir out
for /r out %%f in (*.class) do del /q "%%f"
set "SOURCES="
for /r src\main\java %%f in (*.java) do set SOURCES=!SOURCES! "%%f"
if not defined SOURCES (
  echo No Java source files found under src\main\java.
  exit /b 1
)
echo Compiling Campus Resource Booking...
javac -encoding UTF-8 -cp "lib\*" -d out !SOURCES!
if errorlevel 1 (
  echo Build failed. Check the compiler messages above.
  exit /b 1
)
if exist src\main\resources (
  xcopy src\main\resources\* out\ /e /i /y >nul
  if errorlevel 1 (
    echo Could not copy application resources to out\.
    exit /b 1
  )
)
echo Build completed successfully.
endlocal
