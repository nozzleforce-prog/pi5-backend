# Quick Modbus PLC read test (requires plc.enabled=true backend or standalone)
param(
    [string] $PlcIp = '169.254.179.226',
    [int] $Port = 502,
    [int] $UnitId = 1,
    [int] $Register = 40001,
    [ValidateSet('read', 'write')]
    [string] $Mode = 'read',
    [int] $Value = 0
)

$ErrorActionPreference = 'Stop'

Write-Host "Testing Modbus TCP $PlcIp`:$Port unit=$UnitId register=$Register" -ForegroundColor Cyan

$ping = Test-Connection $PlcIp -Count 1 -Quiet -ErrorAction SilentlyContinue
$tcp = $false
try {
    $c = New-Object System.Net.Sockets.TcpClient
    $a = $c.BeginConnect($PlcIp, $Port, $null, $null)
    if ($a.AsyncWaitHandle.WaitOne(2000)) { $c.EndConnect($a); $tcp = $true; $c.Close() }
} catch {}

Write-Host "  Ping: $ping"
Write-Host "  TCP $Port`: $tcp"

if (-not $tcp) {
    Write-Host "PLC not reachable. Run setup-industrial-network.ps1 as Administrator first." -ForegroundColor Red
    exit 1
}

& (Join-Path $PSScriptRoot 'invoke-plc-java.ps1') `
    -MainClass 'com.ticket.backend.plc.PlcConnectionTest' `
    -JavaArgs @($PlcIp, $Port, $UnitId, $Register, $Mode, $Value)
