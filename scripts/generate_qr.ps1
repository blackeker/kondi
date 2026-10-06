param([string]$code)
$html = @"
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Kondi ADB Karekod</title>
<style>
  body { background: #0f0f1a; color: white; font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
  .card { background: #1e1e2f; padding: 35px; border-radius: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); text-align: center; }
  h2 { color: #ff2e93; margin-bottom: 8px; }
  p { color: #aaa; font-size: 14px; margin-bottom: 24px; }
  img { border: 10px solid white; border-radius: 12px; }
  .code { margin-top: 18px; font-family: monospace; background: #12121c; padding: 12px; border-radius: 8px; color: #00ffaa; font-size: 16px; }
</style>
</head>
<body>
  <div class="card">
    <h2>Kondi ADB Karekod ile Eşleştirme</h2>
    <p>Telefondan: <b>Geliştirici Seçenekleri > Kablosuz Hata Ayıklama > Karekod ile cihaz eşle</b> seçip taratın.</p>
    <img src="https://api.qrserver.com/v1/create-qr-code/?size=320x320&data=WIFI:T:ADB;S:studio-kondi-$code;P:$code;;" width="320" height="320"/>
    <div class="code">Servis: studio-kondi-$code | Şifre: $code</div>
  </div>
</body>
</html>
"@
if (!(Test-Path "logs")) { New-Item -ItemType Directory -Path "logs" }
Set-Content -Path "logs\qr_pair.html" -Value $html -Encoding UTF8
Start-Process "logs\qr_pair.html"
