# Backend icin JAVA_HOME (Maven + Spring Boot)
$script:BackendJavaHome = "C:\Users\Mefebe\.jdks\openjdk-24.0.1"

if (-not (Test-Path "$script:BackendJavaHome\bin\java.exe")) {
    throw "JDK bulunamadi: $script:BackendJavaHome"
}

$env:JAVA_HOME = $script:BackendJavaHome
$javaBin = "$($env:JAVA_HOME)\bin"
$system = @(
    "$env:SystemRoot\System32"
    "$env:SystemRoot\System32\WindowsPowerShell\v1.0"
)
$env:Path = ($javaBin, $system, ($env:Path -split ';' | Where-Object { $_ -and $_ -ne $javaBin })) -join ';'

function Get-BackendMavenCmd {
    $cached = Get-ChildItem -Path "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-*" -Recurse -Filter "mvn.cmd" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($cached) {
        return $cached.FullName
    }
    return $null
}
