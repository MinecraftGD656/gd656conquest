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
$nativeJar = Join-Path $projectDirectory ('libs/ragdollified-1.20.1-' + (Read-Property 'ragdollified_version') + '.jar')
if (!$GradleUserHome) {
    $shared = Join-Path (Split-Path $projectDirectory -Parent) 'build-tools/gradle-cache'
    $GradleUserHome = if (Test-Path -LiteralPath $shared) { $shared } else { Join-Path $projectDirectory '.gradle-user-home' }
}
$forgeVersion = (Read-Property 'minecraft_version') + '-' + (Read-Property 'forge_version')
$srgForge = Join-Path $GradleUserHome "caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/$forgeVersion/forge-$forgeVersion-srg.jar"
foreach ($requiredFile in @($java, $javac, $jar, $nativeJar, $srgForge)) {
    if (!(Test-Path -LiteralPath $requiredFile)) { throw "Missing verification input: $requiredFile" }
}
if (!$DependencyClasspath) {
    $export = Join-Path $projectDirectory 'build/migration-verification/dependencies.txt'
    if (!(Test-Path -LiteralPath $export)) { throw 'Run Gradle writeMigrationVerificationClasspath before this verifier.' }
    $DependencyClasspath = [IO.File]::ReadAllText($export).Trim()
}
$output = Join-Path $projectDirectory 'build/migration-verification/body'
$services = Join-Path $output 'META-INF/services'
[IO.Directory]::CreateDirectory($services) | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $services 'org.spongepowered.asm.service.IMixinService'), 'tests.IsolationMixinService', $utf8)
[IO.File]::WriteAllText((Join-Path $services 'org.spongepowered.asm.service.IGlobalPropertyService'), 'tests.IsolationProperties', $utf8)
# Use the actual body entries from this project's configuration, excluding later migration stages.
$config = Get-Content -LiteralPath (Join-Path $projectDirectory 'src/main/resources/gd656conquest.mixins.json') -Raw | ConvertFrom-Json
$config.mixins = @($config.mixins | Where-Object { $_ -like 'compat.ragdoll.*' })
$config.client = @($config.client | Where-Object { $_ -like 'client.compat.ragdoll.*' })
$config.PSObject.Properties.Remove('refmap') # All optional external body targets use remap=false.
[IO.File]::WriteAllText((Join-Path $output 'migration-body.mixins.json'), ($config | ConvertTo-Json -Depth 8), $utf8)
$classpath = @($jar, $nativeJar, $srgForge, $DependencyClasspath) -join [IO.Path]::PathSeparator
function Invoke-JavaArguments([string]$Executable, [string[]]$Arguments, [string]$Name) {
    $argumentFile = Join-Path $output "$Name.args"
    [IO.File]::WriteAllLines($argumentFile, ($Arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }), $utf8)
    & $Executable "@$argumentFile"
    if ($LASTEXITCODE -ne 0) { throw "Body verification failed: $Name (exit $LASTEXITCODE)" }
}
$sources = @('IsolationMixinService.java', 'IsolationProperties.java', 'BodyMixinCheck.java',
    'RagdollScopeTest.java', 'BodyPolicyCheck.java', 'BodyPacketCheck.java', 'RealScopedPacketTest.java', 'RealReplacementPipelineTest.java') |
    ForEach-Object { Join-Path $PSScriptRoot "migration-tests/$_" }
Invoke-JavaArguments $javac (@('--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', $classpath, '-d', $output) + $sources) 'compile'
$runtimeClasspath = $output + [IO.Path]::PathSeparator + $classpath
foreach ($check in @('RagdollScopeTest', 'BodyPolicyCheck', 'BodyPacketCheck')) {
    Invoke-JavaArguments $java @('-cp', $runtimeClasspath, "tests.$check") $check
}
foreach ($side in @('CLIENT', 'SERVER', 'ABSENT')) {
    Invoke-JavaArguments $java @('-Dmixin.service=tests.IsolationMixinService',
        ('-Dgd.isolation.absent=' + ($side -eq 'ABSENT').ToString().ToLowerInvariant()),
        '-cp', $runtimeClasspath, 'tests.BodyMixinCheck', $side, (Join-Path $output "transformed-$side")) "mixins-$side"
}
Invoke-JavaArguments $java @('-cp', $runtimeClasspath, 'tests.RealScopedPacketTest',
    (Join-Path $output 'transformed-CLIENT'), $nativeJar) 'native-packets-playback'
Invoke-JavaArguments $java @('-cp', $runtimeClasspath, 'tests.RealReplacementPipelineTest',
    (Join-Path $output 'transformed-CLIENT'), $nativeJar, 'fixed') 'native-replacement-senders'
