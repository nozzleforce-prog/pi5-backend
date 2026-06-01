# Cursor Terminal: RFID test modu — kart ID'leri bu pencerede gorunur
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "java-env.ps1")
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Onceki Java/backend sureclerini kapat (8080/8081/2000)
foreach ($port in 8080, 8081, 2000) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}
Start-Sleep -Seconds 1

Write-Host ""
Write-Host '=== RFID test - kart okutunca terminalde KART ID gorunur ===' -ForegroundColor Cyan
Write-Host '    HTTP: 8081  |  RFID TCP: 2000  |  Durdurmak: Ctrl+C' -ForegroundColor DarkGray
Write-Host ""

& (Join-Path $PSScriptRoot "run-backend.ps1") -RfidTest
