# Device + Operation kullanici simulasyon testi
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "java-env.ps1")
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host ""
Write-Host "=== DeviceOperationUserSimulationTest ===" -ForegroundColor Cyan
Write-Host "MongoDB localhost:27017 uzerinde ticket_test DB kullanilir." -ForegroundColor DarkGray
Write-Host ""

$mvn = Get-BackendMavenCmd
& $mvn test "-Dtest=DeviceOperationUserSimulationTest" "-Dspring.profiles.active=test"
