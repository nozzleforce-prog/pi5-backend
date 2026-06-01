# Spring Boot backend — JAVA_HOME ile calistirir
param(
    [ValidateSet("run", "compile", "test", "package")]
    [string]$Goal = "run",
    [switch]$SkipTests,
    [switch]$RfidTest
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "java-env.ps1")

$mvnArgs = @()
if ($SkipTests -or $Goal -ne "test") {
    $mvnArgs += "-DskipTests"
}
$mvnGoal = switch ($Goal) {
    "compile" { @("compile") }
    "test"    { @("test") }
    "package" { @("package") }
    default   { @("spring-boot:run") }
}
$mvnArgs += $mvnGoal
if ($RfidTest -and $Goal -eq "run") {
    $mvnArgs += "-Dspring-boot.run.arguments=--spring.profiles.active=rfid-test"
}

$mvn = Get-BackendMavenCmd
if (-not $mvn) {
    $mvnw = Join-Path $root "mvnw.cmd"
    if (-not (Test-Path $mvnw)) {
        throw "Maven bulunamadi. Once: .\mvnw.cmd compile veya Maven kurun."
    }
    $mvn = $mvnw
}

Push-Location $root
try {
    Write-Host "[*] JAVA_HOME=$env:JAVA_HOME" -ForegroundColor DarkGray
    Write-Host "[*] Maven: $mvn $($mvnArgs -join ' ')" -ForegroundColor DarkGray
    & $mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
