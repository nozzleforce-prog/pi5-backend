@echo off
set "JAVA_HOME=C:\Users\Mefebe\.jdks\openjdk-24.0.1"
set "PATH=%JAVA_HOME%\bin;%SystemRoot%\System32;%SystemRoot%\System32\WindowsPowerShell\v1.0;%PATH%"
cd /d "%~dp0"
for /f "delims=" %%M in ('dir /s /b "%USERPROFILE%\.m2\wrapper\dists\apache-maven-*\bin\mvn.cmd" 2^>nul') do set "MVN=%%M" & goto :found
call mvnw.cmd spring-boot:run %*
goto :eof
:found
call "%MVN%" -DskipTests spring-boot:run %*
