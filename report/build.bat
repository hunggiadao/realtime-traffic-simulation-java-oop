@echo off
setlocal enabledelayedexpansion

set NEW_PATH=
for %%i in ("%PATH:;=";"%") do (
    set "item=%%~i"
    echo !item! | findstr /i "\.exe$" >nul
    if !ERRORLEVEL! NEQ 0 (
        if defined NEW_PATH (
            set "NEW_PATH=!NEW_PATH!;!item!"
        ) else (
            set "NEW_PATH=!item!"
        )
    )
)

set "PATH=%NEW_PATH%"

echo Checking for required packages...
miktex packages list --installed tracklang >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Installing tracklang...
    miktex packages install tracklang
)

miktex packages list --installed datatool >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Installing datatool...
    miktex packages install datatool
)

echo Updating MiKTeX database...
initexmf --update-fndb
initexmf --mkmaps

echo Building thesis.pdf...
latexmk -pdf thesis.tex

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful! PDF generated: thesis.pdf
) else (
    echo.
    echo Build failed! Check the output above for errors.
    exit /b 1
)
