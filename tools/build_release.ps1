param([string[]]$AdditionalTasks = @())
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain-state.json') -Raw | ConvertFrom-Json
$signingFile = Join-Path $config.toolchainRoot 'signing\release-signing.properties'
if (-not (Test-Path -LiteralPath $signingFile)) {
    throw 'Create your local key first: python tools/prepare_release_signing.py'
}
$previousSigningFile = $env:BOSS_RELEASE_SIGNING_FILE
try {
    $env:BOSS_RELEASE_SIGNING_FILE = $signingFile
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks (@(':core-game:test', ':app-boss:assemblePleosRelease', '--no-daemon') + $AdditionalTasks)
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    $source = Join-Path $config.toolchainRoot 'build\app-boss\outputs\apk\pleos\release\app-boss-pleos-release.apk'
    $folder = Join-Path $root 'apk'
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $destination = Join-Path $folder 'Daeho-AI_ExpeditionBoss_release.apk'
    & (Join-Path $config.androidSdk 'build-tools\35.0.0\apksigner.bat') verify --verbose --print-certs $source
    if ($LASTEXITCODE -ne 0) { throw 'Release signature verification failed.' }
    Copy-Item -LiteralPath $source -Destination $destination
    [ordered]@{
        file = [IO.Path]::GetFileName($destination)
        applicationId = 'dev.pleos.entry.boss'
        versionName = '0.2.0'
        versionCode = 2
        buildType = 'release'
        debuggable = $false
        bytes = (Get-Item -LiteralPath $destination).Length
        sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $folder 'release-manifest.json') -Encoding UTF8
    Write-Host "Signed release APK: $destination"
} finally {
    $env:BOSS_RELEASE_SIGNING_FILE = $previousSigningFile
}
