@echo off
setlocal enabledelayedexpansion

set SOURCE_DIR=C:\Users\273273\Desktop\project\Fullstack\AskHr\backend\AskHr\src
set OUTPUT_FILE=C:\Users\273273\Desktop\allcode.txt

if exist "%OUTPUT_FILE%" del "%OUTPUT_FILE%"

for /R "%SOURCE_DIR%" %%f in (*.java *.yml *.yaml *.properties *.sql *.st) do (
    echo %%f | findstr /I "\\target\\" >nul
    if errorlevel 1 (
        echo ===== %%f ===== >> "%OUTPUT_FILE%"
        type "%%f" >> "%OUTPUT_FILE%"
        echo. >> "%OUTPUT_FILE%"
    )
)

echo Done!
pause
