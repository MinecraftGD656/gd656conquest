[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome,
    [string]$DependencyClasspath
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) { throw 'Supply -JavaHome <Java 17 JDK> or set JAVA_HOME.' }
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$properties = Get-Content -LiteralPath (Join-Path $projectDirectory 'gradle.properties')
function Read-Property([string]$Name) {
    $line = $properties | Where-Object { $_ -match ('^' + [regex]::Escape($Name) + '=') } | Select-Object -First 1
    if (!$line) { throw "Missing property: $Name" }
    return ($line -split '=', 2)[1].Trim()
}
$version = (Read-Property 'mod_version') + '-bf1-' + (Read-Property 'migration_version')
$jar = Join-Path $projectDirectory "build/libs/gd656conquest-$version.jar"
$killiconJar = Join-Path $projectDirectory ('libs/gd656killicon-' + (Read-Property 'killicon_version') + '.jar')
if (!$GradleUserHome) {
    $shared = Join-Path (Split-Path $projectDirectory -Parent) 'build-tools/gradle-cache'
    $GradleUserHome = if (Test-Path -LiteralPath $shared) { $shared } else { Join-Path $projectDirectory '.gradle-user-home' }
}
$forgeVersion = (Read-Property 'minecraft_version') + '-' + (Read-Property 'forge_version')
$srgForge = Join-Path $GradleUserHome "caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/$forgeVersion/forge-$forgeVersion-srg.jar"
foreach ($inputFile in @($java, $javac, $jar, $srgForge, $killiconJar)) {
    if (!(Test-Path -LiteralPath $inputFile)) { throw "Missing verification input: $inputFile" }
}
if (!$DependencyClasspath) {
    $export = Join-Path $projectDirectory 'build/migration-verification/dependencies.txt'
    if (!(Test-Path -LiteralPath $export)) { throw 'Run Gradle writeMigrationVerificationClasspath before this verifier.' }
    $DependencyClasspath = [IO.File]::ReadAllText($export).Trim()
}
$output = Join-Path $projectDirectory 'build/migration-verification/death-causes'
$services = Join-Path $output 'META-INF/services'
[IO.Directory]::CreateDirectory($services) | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $services 'org.spongepowered.asm.service.IMixinService'), 'tests.IsolationMixinService', $utf8)
[IO.File]::WriteAllText((Join-Path $services 'org.spongepowered.asm.service.IGlobalPropertyService'), 'tests.IsolationProperties', $utf8)
$config = Get-Content -LiteralPath (Join-Path $projectDirectory 'src/main/resources/gd656conquest.mixins.json') -Raw | ConvertFrom-Json
$config.mixins = @($config.mixins | Where-Object { $_ -in @('server.KillCommandMixin', 'server.ServerPlayerDeathMixin') })
$config.client = @()
[IO.File]::WriteAllText((Join-Path $output 'migration-death-causes.mixins.json'), ($config | ConvertTo-Json -Depth 8), $utf8)
$classpath = @($jar, $srgForge, $DependencyClasspath) -join [IO.Path]::PathSeparator
function Invoke-JavaArguments([string]$Executable, [string[]]$Arguments, [string]$Name) {
    $argumentFile = Join-Path $output "$Name.args"
    [IO.File]::WriteAllLines($argumentFile, ($Arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }), $utf8)
    & $Executable "@$argumentFile"
    if ($LASTEXITCODE -ne 0) { throw "Death-cause verification failed: $Name (exit $LASTEXITCODE)" }
}
$sources = @('IsolationMixinService.java', 'IsolationProperties.java', 'DeathCauseRulesCheck.java',
    'DeathCausePacketCheck.java', 'DeathCauseMixinCheck.java', 'DeathCauseWiringCheck.java') | ForEach-Object { Join-Path $PSScriptRoot "migration-tests/$_" }
Invoke-JavaArguments $javac (@('--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', $classpath, '-d', $output) + $sources) 'compile'
$runtimeClasspath = $output + [IO.Path]::PathSeparator + $classpath
foreach ($check in @('DeathCauseRulesCheck', 'DeathCausePacketCheck')) {
    Invoke-JavaArguments $java @('-cp', $runtimeClasspath, "tests.$check") $check
}
Invoke-JavaArguments $java @('-Dmixin.service=tests.IsolationMixinService', '-cp', $runtimeClasspath,
    'tests.DeathCauseMixinCheck') 'actual-forge-mixins'
Invoke-JavaArguments $java @('-cp', $runtimeClasspath, 'tests.DeathCauseWiringCheck', $jar, $killiconJar) 'production-wiring'
