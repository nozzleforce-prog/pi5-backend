# PC + RP2350 + PLC on ASIX USB Ethernet — single subnet 169.254.179.x
# Run as Administrator
param(
    [string] $PcIp = '169.254.179.1',
    [string] $RfidCardIp = '169.254.179.2',
    [string] $PlcIp = '169.254.179.226',
    [string] $AdapterName = '',
    [int] $Prefix = 24,
    [int] $ModbusPort = 502,
    [switch] $NoElevate
)

function Test-IsAdmin {
    ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not $NoElevate -and -not (Test-IsAdmin)) {
    Start-Process powershell -Verb RunAs -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $PSCommandPath, '-NoElevate'
    ) -Wait
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'

function Get-AsixAdapter {
    param([string]$PreferredName)
    if ($PreferredName) {
        $a = Get-NetAdapter -Name $PreferredName -ErrorAction SilentlyContinue
        if ($a) { return $a }
    }
    return Get-NetAdapter -ErrorAction SilentlyContinue |
        Where-Object { $_.InterfaceDescription -like '*ASIX*' } |
        Select-Object -First 1
}

function Test-TcpPort([string]$Ip, [int]$Port, [int]$TimeoutMs = 500) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect($Ip, $Port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne($TimeoutMs)) {
            $client.EndConnect($async)
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

function Set-AdapterSingleIp([string]$Name, [string]$Ip, [int]$Prefix = 24) {
    $adapter = Get-NetAdapter -Name $Name -ErrorAction SilentlyContinue
    if (-not $adapter) {
        Write-Host "  Adapter '$Name' not found" -ForegroundColor Yellow
        return $false
    }

    $mask = if ($Prefix -eq 16) { '255.255.0.0' } else { '255.255.255.0' }
    Write-Host ('  ' + $Name + ' (' + $adapter.InterfaceDescription + ', ' + $adapter.Status + ') -> ' + $Ip + '/' + $Prefix) -ForegroundColor Cyan
    if ($adapter.Status -ne 'Up') {
        Write-Host '    Cable not linked - IP will apply when link comes up' -ForegroundColor Yellow
    }

    Set-NetIPInterface -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -Dhcp Disabled -ErrorAction SilentlyContinue
    Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $adapter.ifIndex -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-NetIPAddress -InputObject $_ -Confirm:$false -ErrorAction SilentlyContinue }
    Start-Sleep -Milliseconds 400

    & netsh interface ipv4 set address name="$Name" static $Ip $mask | Out-Null
    Start-Sleep -Seconds 1

    $row = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $adapter.ifIndex -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -eq $Ip } | Select-Object -First 1
    if ($row) {
        Write-Host ('    OK: ' + $Ip + ' (' + $row.AddressState + ')') -ForegroundColor Green
    } else {
        Write-Host '    WARN: IP not confirmed' -ForegroundColor Yellow
    }

    foreach ($port in @(502, 2000)) {
        $fw = "Industrial TCP $port"
        if (-not (Get-NetFirewallRule -DisplayName $fw -ErrorAction SilentlyContinue)) {
            New-NetFirewallRule -DisplayName $fw -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port -Profile Any | Out-Null
        }
    }
    return $true
}

$asix = Get-AsixAdapter -PreferredName $AdapterName
if (-not $asix) {
    Write-Host 'ASIX USB Ethernet adapter not found.' -ForegroundColor Red
    exit 1
}
$asixName = $asix.Name

Write-Host '=== Industrial network setup (169.254.179.x) ===' -ForegroundColor Cyan
Write-Host "Adapter (ASIX USB): $asixName"
Write-Host "PC:               $PcIp"
Write-Host "RP2350:           $RfidCardIp"
Write-Host "PLC:              $PlcIp"
Write-Host ''

Write-Host '=== Step 1: Set PC static IP on ASIX ===' -ForegroundColor Cyan
Set-AdapterSingleIp -Name $asixName -Ip $PcIp -Prefix $Prefix | Out-Null

Write-Host "`n=== Step 2: Ping devices ===" -ForegroundColor Cyan
$rfidPing = Test-Connection $RfidCardIp -Count 1 -Quiet -ErrorAction SilentlyContinue
$plcPing = Test-Connection $PlcIp -Count 1 -Quiet -ErrorAction SilentlyContinue
Write-Host ('  RP2350 ' + $RfidCardIp + ' ping=' + $rfidPing)
Write-Host ('  PLC    ' + $PlcIp + ' ping=' + $plcPing)

Write-Host "`n=== Step 3: Modbus PLC ===" -ForegroundColor Cyan
$modbus = Test-TcpPort $PlcIp $ModbusPort 1500
Write-Host ('  ' + $PlcIp + ':' + $ModbusPort + ' = ' + $modbus)

Write-Host "`n=== IPv4 on ASIX ===" -ForegroundColor Cyan
Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $asix.ifIndex |
    Format-Table InterfaceAlias, IPAddress, PrefixLength, AddressState -AutoSize

Write-Host '=== Topology (switch or direct) ===' -ForegroundColor Cyan
Write-Host ('  PC ASIX ' + $PcIp + '  |  RP2350 ' + $RfidCardIp + '  |  PLC ' + $PlcIp)
Write-Host '  RP2350 firmware must be reflashed with network_config.h (169.254.179.2)'
Write-Host ('  Backend: plc.ip=' + $PlcIp + ', rfid unit-targets=' + $RfidCardIp + ':1000')
Write-Host ''

if ($plcPing -and $modbus) { exit 0 }
if ($plcPing -or $rfidPing) { exit 0 }
exit 1
