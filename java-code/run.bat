@echo off
setlocal EnableDelayedExpansion

set "SUMO_BIN="

if defined SUMO_HOME (
    if "!SUMO_HOME:~-1!"=="\" set "SUMO_HOME=!SUMO_HOME:~0,-1!"
    if exist "!SUMO_HOME!\bin" (
        set "SUMO_BIN=!SUMO_HOME!\bin"
        goto :FoundSumo
    )
)

where sumo >nul 2>nul
if %errorlevel% equ 0 (
    for /f "delims=" %%i in ('where sumo') do set "SUMO_EXE=%%i"
    for %%F in ("!SUMO_EXE!") do set "SUMO_BIN=%%~dpF"
    if "!SUMO_BIN:~-1!"=="\" set "SUMO_BIN=!SUMO_BIN:~0,-1!"
    goto :FoundSumo
)

set "COMMON_PATHS=C:\Program Files (x86)\Eclipse\Sumo\bin;C:\Program Files\Eclipse\Sumo\bin;C:\Sumo\bin"
for %%P in ("%COMMON_PATHS:;=" "%") do (
    if exist "%%~P\sumo.exe" (
        set "SUMO_BIN=%%~P"
        goto :FoundSumo
    )
)

:NotFound
echo SUMO not found.
exit /b 1

:FoundSumo
set "TRAAS_JAR=!SUMO_BIN!\TraaS.jar"
set "LIBTRACI_JAR="

for %%f in ("!SUMO_BIN!\libtraci-*.jar") do set "LIBTRACI_JAR=%%f"

if not exist "!TRAAS_JAR!" (
    echo TraaS.jar not found in !SUMO_BIN!
    exit /b 1
)

if /i "%1"=="build" goto :BuildExe

if not exist bin mkdir bin
javac -d bin -cp "lib/javafx25/*;!TRAAS_JAR!;!LIBTRACI_JAR!" src/Main.java src/MainUI.java
if %errorlevel% neq 0 (
    exit /b %errorlevel%
)

java -cp "bin;lib/javafx25/*;!TRAAS_JAR!;!LIBTRACI_JAR!" Main
if %errorlevel% neq 0 (
    exit /b %errorlevel%
)
exit /b 0

:BuildExe

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist

mkdir build\classes
mkdir build\libs

javac -d build/classes -cp "lib/javafx25/*;!TRAAS_JAR!;!LIBTRACI_JAR!" src/Main.java src/MainUI.java
if %errorlevel% neq 0 (
    echo Compilation failed.
    exit /b 1
)

if exist ui xcopy /s /y /i ui build\classes\ui >nul

copy /y "!TRAAS_JAR!" build\libs\ >nul
if defined LIBTRACI_JAR copy /y "!LIBTRACI_JAR!" build\libs\ >nul
copy /y lib\javafx25\*.jar build\libs\ >nul

jar cf build\TrafficSim.jar -C build\classes .

set "JP_CP="
for %%f in (build\libs\*.jar) do set "JP_CP=!JP_CP!libs/%%~nxf;"

jpackage ^
  --type exe ^
  --input build ^
  --dest dist ^
  --name "TrafficSim" ^
  --main-jar TrafficSim.jar ^
  --main-class Main ^
  --win-console ^
  --classpath "!JP_CP!"

if %errorlevel% equ 0 (
    echo Build successful!
) else (
    echo Build failed.
)
exit /b 0
