# Test TCP connection to RP2040/RP2350 and print RFID card scans
param(
    [string] $ReaderIp = '169.254.179.2',
    [int] $ListenPort = 2000,
    [string] $PcIp = '169.254.179.1',
    [string] $DeviceId = '1',
    [switch] $SkipPortCleanup
)

$ErrorActionPreference = 'Stop'

Write-Host ''
Write-Host 'RFID connection test' -ForegroundColor Cyan
Write-Host "  Reader IP : $ReaderIp"
Write-Host "  PC IP     : $PcIp (CH9120 hedefi - firmware bunu kullanir)"
Write-Host "  Listen    : 0.0.0.0:$ListenPort (RP2040 TCP client buraya baglanir)"
Write-Host "  Device ID : $DeviceId"
Write-Host ''

# --- PC IP check ---
Write-Host 'Step 1: PC IP on industrial subnet...' -ForegroundColor Yellow
$pcAddr = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -eq $PcIp -and $_.AddressState -eq 'Preferred' } |
    Select-Object -First 1
if ($pcAddr) {
    Write-Host "  OK  $PcIp on $($pcAddr.InterfaceAlias)" -ForegroundColor Green
} else {
    Write-Host "  WARN  $PcIp not active on any adapter" -ForegroundColor Red
    Write-Host "  Run as Admin: d:\projects\backend\scripts\setup-industrial-network.ps1" -ForegroundColor DarkYellow
}

# --- network check ---
Write-Host ''
Write-Host 'Step 2: Reach reader (ping optional)...' -ForegroundColor Yellow
$pingOk = Test-Connection -ComputerName $ReaderIp -Count 1 -Quiet -ErrorAction SilentlyContinue
if ($pingOk) {
    Write-Host "  OK  Ping $ReaderIp" -ForegroundColor Green
} else {
    Write-Host "  NOTE  Ping failed - CH9120 may still connect over TCP (ICMP kapali olabilir)" -ForegroundColor DarkYellow
    $arp = arp -a | Select-String $ReaderIp
    if ($arp) {
        Write-Host "  OK  ARP entry: $($arp.Line.Trim())" -ForegroundColor Green
    }
}

Write-Host ''
Write-Host 'Step 3: Free listen port (stop backend if running)...' -ForegroundColor Yellow
if (-not $SkipPortCleanup) {
    $listeners = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $listeners) {
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        Write-Host "  Stopping PID $($conn.OwningProcess) ($($proc.ProcessName)) on port $ListenPort"
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
}

$stillBusy = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
if ($stillBusy) {
    Write-Host "  FAIL  Port $ListenPort still in use. Stop backend manually." -ForegroundColor Red
    exit 1
}
Write-Host "  OK  Port $ListenPort is free" -ForegroundColor Green

Write-Host ''
Write-Host 'Step 4: Listen for RFID scans (Ctrl+C to exit)...' -ForegroundColor Yellow
Write-Host '  Beklenen: CONNECTED: /169.254.179.2:1000' -ForegroundColor DarkGray
Write-Host '  Sonra kart okutunca: [RAW] 1,cardId' -ForegroundColor DarkGray
Write-Host '  OLED ekranda UID degismiyorsa sorun USB okuyucuda (TCP degil)' -ForegroundColor DarkGray
Write-Host ''

& (Join-Path $PSScriptRoot 'invoke-rfid-java.ps1') `
    -MainClass 'com.ticket.backend.rfid.RfidListenTest' `
    -JavaArgs @($ListenPort, $ReaderIp, $DeviceId)
