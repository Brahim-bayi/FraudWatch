@echo off
title FraudWatch - Lancement

set ADB="C:\Users\ASUS TUF\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set EMULATOR="C:\Users\ASUS TUF\AppData\Local\Android\Sdk\emulator\emulator.exe"
set PROJECT="C:\Users\ASUS TUF\Downloads\FraudWatch"
set OLLAMA="C:\Users\ASUS TUF\AppData\Local\Programs\Ollama\ollama.exe"

echo ================================
echo   FraudWatch - Theme Rouge
echo ================================
echo.

:: 1. Lancer Ollama si installe
if exist %OLLAMA% (
    tasklist | findstr /i "ollama.exe" >nul
    if errorlevel 1 (
        echo [IA] Demarrage Ollama...
        start "" %OLLAMA% serve
        timeout /t 3 /nobreak >nul
        echo [IA] Telechargement modele moondream (1ere fois seulement)...
        start "" %OLLAMA% pull moondream
    ) else (
        echo [IA] Ollama deja actif
    )
) else (
    echo [IA] Ollama non installe - mode demo actif
)

:: 2. Demarrer emulateur si pas actif
%ADB% devices | findstr "emulator" >nul
if %errorlevel% == 0 (
    echo [OK] Emulateur deja actif
) else (
    echo [1/3] Demarrage de l'emulateur...
    start "" %EMULATOR% -avd Medium_Phone_API_36.1
    echo Attente du demarrage...
    :wait
    timeout /t 5 /nobreak >nul
    %ADB% shell getprop sys.boot_completed 2>nul | findstr "1" >nul
    if errorlevel 1 goto wait
    echo [OK] Emulateur pret
)

:: 3. Build et installer
echo.
echo [2/3] Build et installation...
cd %PROJECT%
call gradlew.bat installDebug
if errorlevel 1 (
    echo [ERREUR] Build echoue
    pause
    exit /b 1
)

:: 4. Lancer l'app
echo.
echo [3/3] Lancement de FraudWatch...
%ADB% shell am start -n "com.fraudwatch/.ui.auth.LoginActivity"

echo.
echo [OK] FraudWatch lance !
timeout /t 3 /nobreak >nul
