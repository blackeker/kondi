@echo off
:: CMD'yi UTF-8 moduna gecirir
chcp 65001 >nul

cd /d "%~dp0"

echo ======================================================
echo   Kondi APK Derleme ve Release Hazırlama Aracı
echo ======================================================
echo.
echo [1/3] APK Dosyaları Derleniyor (Release ve Debug)...
echo.

call .\gradlew.bat clean assembleDebug assembleRelease

if %errorlevel% neq 0 (
    echo.
    echo [HATA] Derleme sırasında hata oluştu! APK'lar üretilemedi.
    pause
    exit /b
)

echo.
echo [2/3] Derleme Başarılı! Dosyalar kopyalanıyor...
echo.

if not exist "build_output" mkdir "build_output"
copy /y "app\build\outputs\apk\debug\app-debug.apk" "build_output\Kondi-debug.apk" >nul
copy /y "app\build\outputs\apk\release\app-release.apk" "build_output\Kondi-release.apk" >nul

echo [+] Dosyalar "build_output" klasörüne kopyalandı:
echo     - build_output\Kondi-debug.apk
echo     - build_output\Kondi-release.apk
echo.

echo ======================================================
echo [3/3] GitHub Release Seçenekleri
echo ======================================================
echo [1] Tarayıcıyı aç ve manuel olarak yükle (Önerilen/Kolay)
echo [2] GitHub Personal Access Token (PAT) ile otomatik yükle
echo [3] Sadece derle ve çık
echo ======================================================
set /p secim="Seçiminiz (1-3): "

if "%secim%"=="1" (
    echo.
    echo [BILGI] Tarayıcıda GitHub Release sayfası açılıyor...
    echo Dosyaları sürükleyip bırakarak manuel yükleyebilirsiniz.
    start "" "https://github.com/blackeker/kondi/releases/new"
    explorer "build_output"
    goto end
)

if "%secim%"=="2" (
    echo.
    set /p token="GitHub Personal Access Token (PAT) girin: "
    set /p tag="Sürüm Etiketi girin (Örn: v1.0.0): "
    
    echo.
    echo [BILGI] GitHub Release oluşturuluyor ve dosyalar yükleniyor...
    
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$token = '%token%';" ^
        "$tag = '%tag%';" ^
        "$repo = 'blackeker/kondi';" ^
        "$headers = @{ 'Authorization' = 'token ' + $token; 'Accept' = 'application/vnd.github.v3+json' };" ^
        "$releaseUri = 'https://api.github.com/repos/' + $repo + '/releases';" ^
        "$releaseBody = @{ 'tag_name' = $tag; 'name' = $tag; 'body' = 'Kondi Otomatik Derleme Sürümü' } | ConvertTo-Json;" ^
        "try {" ^
        "   $release = Invoke-RestMethod -Uri $releaseUri -Method Post -Headers $headers -Body $releaseBody -ContentType 'application/json';" ^
        "   $uploadUrl = $release.upload_url.Split('{')[0];" ^
        "   Write-Host '[+] Release oluşturuldu. Dosyalar yükleniyor...';" ^
        "   foreach ($file in @('build_output\Kondi-debug.apk', 'build_output\Kondi-release.apk')) {" ^
        "       $fileName = Split-Path $file -Leaf;" ^
        "       $uploadUri = $uploadUrl + '?name=' + $fileName;" ^
        "       $fileBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $file));" ^
        "       $upload = Invoke-RestMethod -Uri $uploadUri -Method Post -Headers $headers -Body $fileBytes -ContentType 'application/vnd.android.package-archive';" ^
        "       Write-Host ('[+] Yüklendi: ' + $fileName);" ^
        "   }" ^
        "   Write-Host '[BAŞARILI] Sürüm yayınlandı!';" ^
        "} catch {" ^
        "   Write-Error $_.Exception.Message;" ^
        "}"
    pause
    goto end
)

:end
echo.
echo İşlem tamamlandı.
exit /b
