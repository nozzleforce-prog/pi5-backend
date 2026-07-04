# Test TCP connection to ESP32-P4-NANO (client -> PC :2000)
param(
    [string] $ReaderIp = '169.254.179.2',
    [int] $ListenPort = 2000,
    [string] $PcIp = '169.254.179.1',
    [string] $DeviceId = '1',
    [switch] $SkipPortCleanup,
    [switch] $UsePythonTest
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$p4Root = Join-Path (Split-Path $repoRoot -Parent) 'RfucbirimlerP4Nano'

Write-Host ''
Write-Host 'RFID P4-NANO connection test' -ForegroundColor Cyan
Write-Host "  Card IP   : $ReaderIp (TCP client)"
Write-Host "  PC IP     : $PcIp (TCP server)"
Write-Host "  Listen    : 0.0.0.0:$ListenPort"
Write-Host ''

Write-Host 'Step 1: PC IP on industrial subnet...' -ForegroundColor Yellow
$pcAddr = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -eq $PcIp -and $_.AddressState -eq 'Preferred' } |
    Select-Object -First 1
if ($pcAddr) {
    Write-Host "  OK  $PcIp on $($pcAddr.InterfaceAlias)" -ForegroundColor Green
} else {
    Write-Host "  WARN  $PcIp not active - run setup-industrial-network.ps1 as Admin" -ForegroundColor Red
}

Write-Host ''
Write-Host 'Step 2: Reach card...' -ForegroundColor Yellow
$pingOk = Test-Connection -ComputerName $ReaderIp -Count 1 -Quiet -ErrorAction SilentlyContinue
if ($pingOk) {
    Write-Host "  OK  Ping $ReaderIp" -ForegroundColor Green
} else {
    Write-Host '  NOTE  Ping failed - ICMP may be disabled on ESP32' -ForegroundColor DarkYellow
}

if (-not $SkipPortCleanup) {
    Write-Host ''
    Write-Host 'Step 3: Free listen port...' -ForegroundColor Yellow
    $listeners = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $listeners) {
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        Write-Host ('  Stopping PID ' + $conn.OwningProcess + ' (' + $proc.ProcessName + ')')
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
}

Write-Host ''
Write-Host 'Step 4: Listen for P4-NANO (Ctrl+C to exit)...' -ForegroundColor Yellow
Write-Host '  Expected connect: Merhaba PC, ESP32-P4-NANO baglandi!' -ForegroundColor DarkGray
Write-Host '  After button+scan: MAKINE:1 KART:uid AD:name' -ForegroundColor DarkGray
Write-Host '  Heartbeat: durum #N - kart calisiyor' -ForegroundColor DarkGray
Write-Host ''

if ($UsePythonTest -and (Test-Path (Join-Path $p4Root 'tools\connection_test.py'))) {
    Push-Location $p4Root
    try {
        python tools/connection_test.py --duration 60
    } finally {
        Pop-Location
    }
} else {
    & (Join-Path $PSScriptRoot 'invoke-rfid-java.ps1') `
        -MainClass 'com.ticket.backend.rfid.RfidListenTest' `
        -JavaArgs @($ListenPort, $ReaderIp, $DeviceId)
}
