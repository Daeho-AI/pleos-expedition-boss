param([string[]]$Tasks = @(":core-game:test", ":app-boss:assembleFixtureDebug", ":app-boss:assemblePleosDebug"))
$ErrorActionPreference = "Stop"
$state = Get-Content -LiteralPath (Join-Path $PSScriptRoot "toolchain-state.json") -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'storage.ps1')
Set-BossStoragePaths -ToolchainRoot $state.toolchainRoot
$env:JAVA_HOME = $state.javaHome
$env:ANDROID_HOME = $state.androidSdk
$env:ANDROID_SDK_ROOT = $state.androidSdk
$env:GRADLE_USER_HOME = Join-Path $state.toolchainRoot "gradle-cache"
Push-Location (Join-Path $PSScriptRoot "..\android")
try {
    $buildRoot = Join-Path $state.toolchainRoot 'build'
    & .\gradlew.bat @Tasks "-PbossBuildRoot=$buildRoot" --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
