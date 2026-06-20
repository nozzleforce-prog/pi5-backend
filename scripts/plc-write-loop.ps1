# Interactive PLC test: enter address + value in a loop, verify in GMT Suite
param(
    [string] $PlcIp = '169.254.179.226',
    [int] $Port = 502,
    [int] $UnitId = 1
)

$ErrorActionPreference = 'Stop'

Write-Host ''
Write-Host 'PLC write loop - stop the backend first so Modbus port is free.' -ForegroundColor Yellow
Write-Host "Target: ${PlcIp}:${Port}  unitId=${UnitId}" -ForegroundColor Cyan
Write-Host ''
Write-Host 'Examples:' -ForegroundColor DarkGray
Write-Host '  Address 40001  Value 1      set modBilgisi to 1' -ForegroundColor DarkGray
Write-Host '  Address 40001  Value 128    set bit 7' -ForegroundColor DarkGray
Write-Host '  Address 40001  Value blank   read only' -ForegroundColor DarkGray
Write-Host '  Address q                     quit' -ForegroundColor DarkGray
Write-Host ''

& (Join-Path $PSScriptRoot 'invoke-plc-java.ps1') `
    -MainClass 'com.ticket.backend.plc.PlcWriteLoop' `
    -JavaArgs @($PlcIp, $Port, $UnitId)
