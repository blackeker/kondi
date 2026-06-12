@echo off
setlocal EnableDelayedExpansion
color 0A
title Kondi İnstaller Bater
set "PACKAGE_NAME=com.myanim.kondi"
if not exist "logs" mkdir logs

:: Saati formatla (HHMM)
set "t=%time: =0%"
set "mytime=%t:~0,2%%t:~3,2%"
:: Tarihi formatla (YYYYMMDD) - Paz 08.03.2026 formatina gore
set "datestr=%date:~10,4%%date:~7,2%%date:~4,2%"

set "BUILD_LOG=logs\buildlog_%datestr%_%mytime%.txt"

:Main
cls
echo  [SYSTEM] Bagli cihazlar taraniyor...
adb start-server >nul 2>&1
set count=0

:: Cihaz tarama döngüsü
for /f "skip=1 tokens=1" %%d in ('adb devices') do (
    set "temp_device=%%d"
    if not "!temp_device!"=="" (
        set /a count+=1
        set "dev[!count!]=!temp_device!"
    )
)

:: CIHAZ YOKSA
if %count%==0 (
    color 0E
    echo.
    echo  [UYARI] Bagli cihaz bulunamadi!
    echo  ---------------------------------------------------------------
    echo  Otomatik APK Build moduna geciliyor...
    timeout /t 3 /nobreak >nul
    color 0A
    set "BUILD_MODE=APK-Only (Cihaz Yok)"
    set "SELECTED_DEVICE=YOK"
    goto :BuildOnly
)

:: CIHAZ VARSA SECIM
if %count%==1 (
    set "SELECTED_DEVICE=!dev[1]!"
) else (
    echo.
    echo  Birden fazla cihaz bulundu:
    for /L %%i in (1,1,%count%) do echo  %%i. !dev[%%i]!
    echo.
    set /p "choice= Cihaz secin [1-%count%]: "
    if "!choice!"=="" set "choice=1"
    for %%i in (!choice!) do set "SELECTED_DEVICE=!dev[%%i]!"
)

set "ADB_CMD=adb -s !SELECTED_DEVICE!"

:Menu
cls
echo.
echo  ==================================================================
echo   HEDEF CIHAZ: !SELECTED_DEVICE!
echo  ==================================================================
echo.
echo   1. Hizli Guncelle   (Veriler Korunur)
echo   2. Temiz Kurulum    (Eski Silinir + Sifirdan Kurulur)
echo   3. Sadece APK Olustur
echo   4. Uygulamayi Kaldir
echo   5. LOGCAT           (Filtreli + Canli Izleme)
echo   6. Log Klasorunu Ac
echo   7. Cikis
echo.
echo   [Build Log: %BUILD_LOG%]
echo  ==================================================================
set /p "ans= Seciminiz [1-7]: "

if "%ans%"=="1" goto :UpdateInstall
if "%ans%"=="2" goto :CleanInstall
if "%ans%"=="3" (set "BUILD_MODE=Sadece APK" & goto :BuildOnly)
if "%ans%"=="4" goto :UninstallOnly
if "%ans%"=="5" goto :Logcat
if "%ans%"=="6" (start "" "logs" & goto :Menu)
if "%ans%"=="7" exit
goto :Menu

:: --- ISLEMLER ---

:BuildOnly
cls
if "!BUILD_MODE!"=="" set "BUILD_MODE=Sadece APK"
echo  [INFO] Log Baslatiliyor...
echo. >> "%BUILD_LOG%"
echo --- BUILD START: %date% %time% --- >> "%BUILD_LOG%"

echo  [!] Gradle assembleDebug calisiyor...
call gradlew.bat assembleDebug > temp_build.log 2>&1
set "BUILD_ERR=%ERRORLEVEL%"

type temp_build.log
type temp_build.log >> "%BUILD_LOG%"
del temp_build.log

if %BUILD_ERR% NEQ 0 (
    echo  SONUC: BASARISIZ >> "%BUILD_LOG%"
    color 0C
    echo.
    echo  [HATA] Derleme basarisiz!
    pause
    color 0A
    if "!SELECTED_DEVICE!"=="YOK" goto :Main
    goto :Menu
)

echo  SONUC: BASARILI >> "%BUILD_LOG%"
echo.
echo  [OK] APK Hazir: app\build\outputs\apk\debug\app-debug.apk
if "!SELECTED_DEVICE!"=="YOK" (
    echo.
    echo  1. APK Klasorunu Ac
    echo  2. Yeniden Cihaz Tara
    echo  3. Cikis
    set /p "next= Secim: "
    if "!next!"=="1" start "" "app\build\outputs\apk\debug"
    if "!next!"=="2" goto :Main
    if "!next!"=="3" exit
)
pause
goto :Menu

:UpdateInstall
cls
echo  [1/2] APK Derleniyor...
call gradlew.bat assembleDebug > temp_build.log 2>&1
set "BUILD_ERR=%ERRORLEVEL%"
type temp_build.log
(echo --- BUILD START: %date% %time% --- & type temp_build.log) >> "%BUILD_LOG%" 2>nul
del temp_build.log 2>nul
if %BUILD_ERR% NEQ 0 goto :Error

echo  [2/2] Yukleniyor...
%ADB_CMD% install -r app\build\outputs\apk\debug\app-debug.apk > temp_install.log 2>&1
set "INST_ERR=%ERRORLEVEL%"
type temp_install.log
type temp_install.log >> "%BUILD_LOG%" 2>nul
del temp_install.log 2>nul
if %INST_ERR% NEQ 0 goto :Error
goto :Success

:CleanInstall
cls
echo  [1/3] Siliniyor...
%ADB_CMD% uninstall %PACKAGE_NAME% > temp_uninst.log 2>&1
type temp_uninst.log
type temp_uninst.log >> "%BUILD_LOG%" 2>nul
del temp_uninst.log 2>nul

echo  [2/3] Temiz Derleme...
call gradlew.bat clean assembleDebug > temp_build.log 2>&1
set "BUILD_ERR=%ERRORLEVEL%"
type temp_build.log
(echo --- CLEAN BUILD START: %date% %time% --- & type temp_build.log) >> "%BUILD_LOG%" 2>nul
del temp_build.log 2>nul
if %BUILD_ERR% NEQ 0 goto :Error

echo  [3/3] Yukleniyor...
%ADB_CMD% install app\build\outputs\apk\debug\app-debug.apk > temp_install.log 2>&1
set "INST_ERR=%ERRORLEVEL%"
type temp_install.log
type temp_install.log >> "%BUILD_LOG%" 2>nul
del temp_install.log 2>nul
if %INST_ERR% NEQ 0 goto :Error
goto :Success

:UninstallOnly
cls
%ADB_CMD% uninstall %PACKAGE_NAME%
pause
goto :Menu

:Logcat
cls
set "mytime=%time: =0%"
set "LOG_PATH=logs\logcat_%date:~10,4%%date:~7,2%%date:~4,2%_%mytime:~0,2%%mytime:~3,2%.txt"
echo  [INFO] Logcat baslatiliyor... (Otomatik Yeniden Baglanma Aktif)
%ADB_CMD% logcat -c
start "Logcat [Kondi]" powershell -NoExit -Command "& { while ($true) { Write-Host 'Logcat baslatildi...'; %ADB_CMD% logcat -v time | Select-String '%PACKAGE_NAME%' | where { $_ -notmatch 'OpenGLRenderer|unimplemented' } | Tee-Object -FilePath '%LOG_PATH%' -Append; Write-Host 'Baglanti koptu (EOF), 2 saniye icinde tekrar baglaniliyor...'; Start-Sleep -Seconds 2 } }"
goto :Menu

:Error
color 0C
echo.
echo  [HATA] Bir seyler ters gitti. Log: %BUILD_LOG%
pause
color 0A
goto :Menu

:Success
echo.
echo  [OK] Basarili! Uygulama aciliyor...
%ADB_CMD% shell am start -n %PACKAGE_NAME%/%PACKAGE_NAME%.MainActivity >nul 2>&1
%ADB_CMD% shell monkey -p %PACKAGE_NAME% -c android.intent.category.LAUNCHER 1 >nul 2>&1
timeout /t 2 /nobreak >nul
goto :Logcat