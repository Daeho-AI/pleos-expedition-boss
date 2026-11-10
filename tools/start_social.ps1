$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$state = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain-state.json') -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'storage.ps1')
Set-BossStoragePaths -ToolchainRoot $state.toolchainRoot
try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8765/health' -TimeoutSec 2
} catch { $health = $null }
if ($health) {
    if ($health.service -eq 'expedition-boss-social' -and $health.version -eq '0.2.0') {
        Write-Host 'Friends server ready: http://127.0.0.1:8765'
        return
    }
    throw 'Port 8765 is already used by another service or server version.'
}
$python = Join-Path $state.toolchainRoot 'social-env\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) { throw 'Run setup.cmd first to install the friends server.' }
$data = Join-Path $state.toolchainRoot 'social-data'
New-Item -ItemType Directory -Path $data -Force | Out-Null
$env:BOSS_SOCIAL_DB = Join-Path $data 'social.sqlite'
$server = Join-Path $root 'server'
$arguments = @('-B','-m','uvicorn','app:app','--app-dir',('"'+$server+'"'),'--host','127.0.0.1','--port','8765','--lifespan','off','--ws','none','--no-access-log','--limit-concurrency','64')
$process = Start-Process -FilePath $python -ArgumentList $arguments -WorkingDirectory $server -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $data 'server.log') -RedirectStandardError (Join-Path $data 'server-error.log')
$process.Id | Set-Content -LiteralPath (Join-Path $data 'server.pid') -Encoding ascii
for ($attempt=0; $attempt -lt 40; $attempt++) {
    Start-Sleep -Milliseconds 250
    if ($process.HasExited) { throw "Friends server stopped. Check $data\server-error.log" }
    try { $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8765/health' -TimeoutSec 1 } catch { continue }
    if ($health.service -eq 'expedition-boss-social' -and $health.version -eq '0.2.0') {
        Write-Host 'Friends server ready: http://127.0.0.1:8765'
        return
    }
}
throw "Friends server did not start. Check $data\server-error.log"
