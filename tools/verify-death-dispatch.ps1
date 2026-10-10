[CmdletBinding()]
param(
    [string]$JavaExecutable = 'java',
    [string]$GradleUserHome,
    [string]$EventBusClasspath
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$productionClasses = Join-Path $projectDirectory 'build/classes/java/main'
if (!(Test-Path -LiteralPath $productionClasses)) { throw 'Build the project before running migration checks.' }

function Invoke-MigrationCheck([string]$Classpath, [string]$SourceFile) {
    & $JavaExecutable -cp $Classpath (Join-Path $PSScriptRoot "migration-tests/$SourceFile")
    if ($LASTEXITCODE -ne 0) { throw "Migration check failed: $SourceFile (exit $LASTEXITCODE)" }
}

Invoke-MigrationCheck $productionClasses 'ReviveTargetSelectionCheck.java'
Invoke-MigrationCheck $productionClasses 'DeferredDeathDispatchCheck.java'

if (!$EventBusClasspath) {
    if (!$GradleUserHome) {
        $workspaceCache = Join-Path (Split-Path $projectDirectory -Parent) 'build-tools/gradle-cache'
        $GradleUserHome = if (Test-Path -LiteralPath $workspaceCache) {
            $workspaceCache
        } else {
            Join-Path $projectDirectory '.gradle-user-home'
        }
    }
    # Real Forge EventBus dependencies already available in this project's build cache.
    $cachedModules = Join-Path $GradleUserHome 'caches/modules-2/files-2.1'
    $modules = @(
        'net.minecraftforge/eventbus/6.2.33',
        'net.jodah/typetools/0.6.3',
        'org.ow2.asm/asm/9.2',
        'org.ow2.asm/asm-tree/9.2',
        'org.apache.logging.log4j/log4j-api/2.19.0',
        'org.apache.logging.log4j/log4j-core/2.19.0'
    )
    $eventBusJars = foreach ($module in $modules) {
        $moduleDirectory = Join-Path $cachedModules $module
        if (!(Test-Path -LiteralPath $moduleDirectory)) {
            throw "Missing cached test dependency: $module. Supply -EventBusClasspath or -GradleUserHome."
        }
        $cachedJar = Get-ChildItem -LiteralPath $moduleDirectory -Recurse -File -Filter '*.jar' | Select-Object -First 1
        if (!$cachedJar) { throw "No JAR for cached test dependency: $module" }
        $cachedJar.FullName
    }
    $EventBusClasspath = $eventBusJars -join [IO.Path]::PathSeparator
}
Invoke-MigrationCheck ($productionClasses + [IO.Path]::PathSeparator + $EventBusClasspath) 'DeferredDeathEventBusCheck.java'
