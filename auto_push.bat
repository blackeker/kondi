@echo off
:: CMD'yi UTF-8 moduna gecirir (Turkce karakterler bozulmaz)
chcp 65001 >nul

:: Git yolunu ekle (Eger PATH'de yoksa)
set "GIT_PATH=C:\Program Files\Git\cmd"
where git >nul 2>nul
if %errorlevel% neq 0 (
    set "PATH=%PATH%;%GIT_PATH%"
)

:: Git'in hala bulunup bulunmadigini kontrol et
git --version >nul 2>nul
if %errorlevel% neq 0 (
    echo [HATA] Git bulunamadı! Lutfen Git'in yuklu oldugundan emin olun.
    pause
    exit /b
)

cd /d "%~dp0"

:: Git deposu olup olmadigini kontrol et
if not exist ".git" (
    echo [BILGI] Git deposu baslatiliyor...
    git init
    git remote add origin https://github.com/blackeker/kondi.git
    git branch -M main
)

echo ======================================================
echo   Kondi GitHub Otomatik Senkronizasyon Çalışıyor...
echo   Durdurmak için bu pencereyi kapatabilirsiniz.
echo ======================================================

:loop
:: Degisiklik olup olmadigini kontrol et (Yeni dosyalar dahil)
git status --porcelain | findstr /r "." >nul
if %errorlevel% neq 0 (
    goto wait_block
)

echo [%time%] Yeni değişiklikler algılandı, pushlanıyor...
git add .
git commit -m "güncelleme"
git pull origin main --no-edit
git push origin main

if %errorlevel% neq 0 (
    echo [%time%] [UYARI] Gönderim sırasında bir hata oluştu. Bir sonraki döngüde tekrar denenecek.
) else (
    echo [%time%] İşlem tamamlandı. Beklemeye geçiliyor.
)
echo --------------------------------------------------

:wait_block
:: 10 saniye bekleme sağlamak için yerel adrese (localhost) ping atıyoruz
ping 127.0.0.1 -n 11 >nul
goto loop
