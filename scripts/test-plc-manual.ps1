# Interactive Modbus PLC read/write test (GMT addresses 40001+)
param(
    [string] $PlcIp = '169.254.179.226',
    [int] $Port = 502,
    [int] $UnitId = 1,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Command
)

$ErrorActionPreference = 'Stop'

Write-Host "PLC manual test — $PlcIp`:$Port unit=$UnitId" -ForegroundColor Cyan
Write-Host "Stop the backend first if port 502 is busy on the PLC side." -ForegroundColor DarkYellow
Write-Host ""

$argsLine = @($PlcIp, $Port, $UnitId) + $Command

& (Join-Path $PSScriptRoot 'invoke-plc-java.ps1') `
    -MainClass 'com.ticket.backend.plc.PlcManualTest' `
    -JavaArgs $argsLine
