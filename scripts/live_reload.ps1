# Kondi Jetpack Compose Live Reload Watcher
$host.UI.RawUI.WindowTitle = "Kondi Live Reload"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  🚀 Kondi Live Reload Watcher Aktif" -ForegroundColor Green
Write-Host "  Dosyayı kaydettiğinizde (Ctrl+S) anında güncellenir..." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
if (-not (Test-Path "$projectRoot\gradlew.bat")) {
    $projectRoot = Get-Location
}

$watchPath = "$projectRoot\app\src\main"
Write-Host "Takip Edilen Dizin: $watchPath" -ForegroundColor DarkGray

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $watchPath
$watcher.Filter = "*.*"
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true

$global:lastRun = [DateTime]::MinValue

$action = {
    $fileExt = [System.IO.Path]::GetExtension($Event.SourceEventArgs.FullPath)
    if ($fileExt -in @('.kt', '.xml', '.json', '.properties')) {
        $now = [DateTime]::Now
        if (($now - $global:lastRun).TotalSeconds -gt 2) {
            $global:lastRun = $now
            $fileName = [System.IO.Path]::GetFileName($Event.SourceEventArgs.FullPath)
            Write-Host "`n[$(Get-Date -Format 'HH:mm:ss')] ⚡ Değişiklik algılandı: $fileName" -ForegroundColor Yellow
            Write-Host "🔄 Derleniyor ve cihaza aktarılıyor..." -ForegroundColor Cyan
            
            Push-Location $using:projectRoot
            
            # Hızlı derleme ve yükleme
            & ".\gradlew.bat" installDebug -q
            
            if ($LASTEXITCODE -eq 0) {
                # Ekranı yeniden odakla
                adb shell am start -n "com.myanim.kondi/.MainActivity" | Out-Null
                Write-Host "✅ Ekran güncellendi!" -ForegroundColor Green
            } else {
                Write-Host "❌ Derleme hatası oluştu, kodu kontrol edin." -ForegroundColor Red
            }
            
            Pop-Location
        }
    }
}

Register-ObjectEvent $watcher 'Changed' -Action $action | Out-Null
Register-ObjectEvent $watcher 'Created' -Action $action | Out-Null

try {
    while ($true) {
        Start-Sleep -Milliseconds 500
    }
}
finally {
    Unregister-Event -SourceIdentifier $watcher.Name -ErrorAction SilentlyContinue
    $watcher.Dispose()
    Write-Host "`nWatcher durduruldu." -ForegroundColor Red
}
