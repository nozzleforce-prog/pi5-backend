# Runs PLC test Java classes without compiling the full Spring Boot backend
param(
    [Parameter(Mandatory = $true)]
    [string] $MainClass,
    [string[]] $JavaArgs = @()
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')

$root = Split-Path -Parent $PSScriptRoot
$toolDir = Join-Path $PSScriptRoot 'plc-tool'
$toolTarget = Join-Path $toolDir 'target'
$classesDir = Join-Path $toolTarget 'classes'

$mvn = Get-BackendMavenCmd
if (-not $mvn) {
    $mvn = Join-Path $root 'mvnw.cmd'
}

Write-Host 'Building PLC tool (jlibmodbus only)...' -ForegroundColor DarkGray
Push-Location $toolDir
& $mvn -q -DskipTests compile
$buildOk = ($LASTEXITCODE -eq 0)
Pop-Location

if (-not $buildOk) {
    Write-Host ''
    Write-Host 'PLC tool build failed. Check internet access to Maven Central.' -ForegroundColor Red
    exit 1
}

$jlibJar = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\repository\com\intelligt\modbus\jlibmodbus') `
    -Recurse -Filter 'jlibmodbus-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jlibJar) {
    Write-Host 'jlibmodbus jar not found in local Maven cache.' -ForegroundColor Red
    exit 1
}

$cp = @($classesDir, $jlibJar.FullName) -join ';'

& "$env:JAVA_HOME\bin\java.exe" -cp $cp $MainClass @JavaArgs
exit $LASTEXITCODE
