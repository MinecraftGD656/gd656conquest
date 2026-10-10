[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$GradleUserHome,
    [switch]$Offline,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

function Test-Jdk17([string]$Candidate) {
    if ([string]::IsNullOrWhiteSpace($Candidate)) { return $false }
    $releaseFile = Join-Path $Candidate 'release'
    $compilerFile = Join-Path $Candidate 'bin/javac.exe'
    $javaFile = Join-Path $Candidate 'bin/java.exe'
    return (Test-Path -LiteralPath $compilerFile) -and
        (Test-Path -LiteralPath $javaFile) -and
        (Test-Path -LiteralPath $releaseFile) -and
        [bool](Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="17(?:\.|\")' -Quiet)
}

if ($JavaHome) {
    if (!(Test-Jdk17 $JavaHome)) { throw '-JavaHome must point to a Java 17 JDK.' }
} else {
    $candidates = @($env:JAVA_HOME)
    foreach ($jdkRoot in @((Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'))) {
        if (Test-Path -LiteralPath $jdkRoot) {
            $candidates += Get-ChildItem -LiteralPath $jdkRoot -Directory -Filter '*17*' |
                Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
        }
    }
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
    $JavaHome = $candidates | Where-Object { Test-Jdk17 $_ } | Select-Object -First 1
    if (!$JavaHome) { throw 'Java 17 JDK not found. Supply -JavaHome <JDK directory>.' }
}

if (!$GradleUserHome) {
    $workspaceCache = Join-Path (Split-Path $PSScriptRoot -Parent) 'build-tools/gradle-cache'
    $GradleUserHome = if (Test-Path -LiteralPath $workspaceCache) {
        $workspaceCache
    } else {
        Join-Path $PSScriptRoot '.gradle-user-home'
    }
}
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$GradleUserHome = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($GradleUserHome)
$logDirectory = Join-Path $PSScriptRoot '.build-logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$logPath = Join-Path $logDirectory 'build.log'
$gradleArguments = @('--gradle-user-home', $GradleUserHome, '--no-daemon', '--console=plain', '--stacktrace')
if ($Offline) { $gradleArguments += '--offline' }
if ($Clean) { $gradleArguments += 'clean' }
$gradleArguments += 'build'
$projectProperties = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'gradle.properties') -Raw | ConvertFrom-StringData

$previousJavaHome = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $JavaHome
    Write-Host "Java 17: $JavaHome"
    Write-Host "Gradle cache: $GradleUserHome"
    Write-Host "Build log: $logPath"
    # Windows PowerShell represents native stderr as ErrorRecord even for warnings.
    try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArguments 2>&1 | Tee-Object -FilePath $logPath -ErrorAction Stop
        $buildExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($buildExitCode -ne 0) { throw "Gradle build failed (exit $buildExitCode). See $logPath" }
    $mainJar = Join-Path $PSScriptRoot "build/libs/$($projectProperties.mod_id)-$($projectProperties.mod_version)-bf1-$($projectProperties.migration_version).jar"
    if (!(Test-Path -LiteralPath $mainJar)) { throw "Expected JAR not found: $mainJar" }
    $artifact = Get-Item -LiteralPath $mainJar
    $artifactHash = (Get-FileHash -LiteralPath $mainJar -Algorithm SHA256).Hash
    Write-Host "JAR: $($artifact.FullName) ($($artifact.Length) bytes)"
    Write-Host "SHA-256: $artifactHash"
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
