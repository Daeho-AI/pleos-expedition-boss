param([ValidateSet('fixture','pleos','all')][string]$Mode = 'fixture')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not ([IO.Path]::GetFullPath($root).StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase))) {
    throw 'Use a checkout under D:\Codex for the Windows helpers.'
}
$manifest = Get-Content -LiteralPath (Join-Path $root 'artifacts\build-manifest.json') -Raw | ConvertFrom-Json
$releaseBase = 'https://github.com/Daeho-AI/pleos-expedition-boss/releases/download/v0.2.0-preview.1'
$folder = Join-Path $root 'artifacts'
New-Item -ItemType Directory -Path $folder -Force | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'
foreach ($entry in $manifest.artifacts) {
    if ($Mode -ne 'all' -and $entry.file -ne "boss-$Mode-debug.apk") { continue }
    if ([IO.Path]::GetFileName($entry.file) -ne $entry.file -or $entry.sha256 -notmatch '^[0-9a-f]{64}$') { throw 'Invalid APK manifest.' }
    $destination = Join-Path $folder $entry.file
    if ((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -eq $entry.sha256) {
        Write-Host "Verified existing APK: $($entry.file)"
        continue
    }
    Write-Host "Downloading APK: $($entry.file)"
    $partial = "$destination.part"
    Invoke-WebRequest -Uri "$releaseBase/$($entry.file)" -OutFile $partial -UseBasicParsing
    if ((Get-Item -LiteralPath $partial).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -ne $entry.sha256) {
        throw "APK checksum mismatch: $($entry.file). Download was not installed."
    }
    Move-Item -LiteralPath $partial -Destination $destination -Force
    Write-Host "Verified downloaded APK: $($entry.file)"
}
