# Compiles and runs standalone RFID listen test (no Spring Boot)
param(
    [Parameter(Mandatory = $true)]
    [string] $MainClass,
    [string[]] $JavaArgs = @()
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')

$toolDir = Join-Path $PSScriptRoot 'rfid-tool'
$classesDir = Join-Path $toolDir 'target\classes'

$mvn = Get-BackendMavenCmd
if (-not $mvn) {
    $root = Split-Path -Parent $PSScriptRoot
    $mvn = Join-Path $root 'mvnw.cmd'
}

Write-Host 'Building RFID tool...' -ForegroundColor DarkGray
Push-Location $toolDir
& $mvn -q -DskipTests compile
$buildOk = ($LASTEXITCODE -eq 0)
Pop-Location

if (-not $buildOk) {
    Write-Host 'RFID tool build failed.' -ForegroundColor Red
    exit 1
}

& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir $MainClass @JavaArgs
exit $LASTEXITCODE
