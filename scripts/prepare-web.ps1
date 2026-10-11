# Run in PowerShell 5+ on Windows. Downloads WordCraft's prebuilt, offline WASM web release.
$ErrorActionPreference = 'Stop'
$version = 'v0.4.0'
$root = Split-Path $PSScriptRoot -Parent
$dest = Join-Path $root 'app/src/main/assets/wordcraft'
$licenses = Join-Path $root 'app/src/main/assets/licenses'
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('wordcraft-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
try {
    $zip = Join-Path $tmp 'wordcraft-web.zip'
    Invoke-WebRequest "https://github.com/storytold/wordcraft/releases/download/$version/wordcraft-web-0.4.0.zip" -OutFile $zip
    $unpacked = Join-Path $tmp 'unpacked'
    Expand-Archive $zip -DestinationPath $unpacked -Force
    $index = Get-ChildItem $unpacked -Recurse -File -Filter 'index.html' | Select-Object -First 1
    if (-not $index) { throw 'No index.html in release ZIP' }
    $wasm = Get-ChildItem $index.Directory.FullName -Recurse -File -Filter '*.wasm' | Select-Object -First 1
    if (-not $wasm) { throw 'No .wasm module in release ZIP' }
    if (Test-Path $dest) { Remove-Item $dest -Force -Recurse }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Copy-Item -Path (Join-Path $index.Directory.FullName '*') -Destination $dest -Recurse -Force
    New-Item -ItemType Directory -Path $licenses -Force | Out-Null
    foreach ($file in @('LICENSE-MIT', 'LICENSE-APACHE', 'NOTICE', 'ATTRIBUTION.md')) {
        Invoke-WebRequest "https://raw.githubusercontent.com/storytold/wordcraft/$version/$file" -OutFile (Join-Path $licenses $file)
    }
    Write-Host 'WordCraft web app bundled. Open this folder in Android Studio and build an APK.'
} finally {
    Remove-Item $tmp -Force -Recurse -ErrorAction SilentlyContinue
}
