param([ValidateSet('fixture','pleos')][string]$Mode = 'fixture', [int]$BootTimeoutSeconds = 480, [ValidateSet('auto','host','software','swiftshader')][string]$GpuMode = 'auto')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $PSScriptRoot 'toolchain-state.json'
if (-not (Test-Path -LiteralPath $statePath)) {
    Write-Host '먼저 setup.cmd를 실행해 개발 도구와 시연 APK를 준비해 주세요.' -ForegroundColor Red
    exit 1
}
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain-state.json') -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'storage.ps1')
Set-BossStoragePaths -ToolchainRoot $config.toolchainRoot
$adb = Join-Path $config.androidSdk 'platform-tools\adb.exe'
$apk = Join-Path $root "artifacts\boss-$Mode-debug.apk"
$work = Join-Path $root 'work'
New-Item -ItemType Directory -Path $work -Force | Out-Null
$log = Join-Path $work 'launcher-last.log'

function Invoke-BossAdb {
    param([string[]]$AdbArguments)
    # PowerShell 5.1 treats native stderr as ErrorRecord objects. ADB's successful
    # daemon startup also writes to stderr: use its exit code to decide success.
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $adb @AdbArguments 2>&1)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    [pscustomobject]@{ ExitCode=$code; Text=(($lines | ForEach-Object { $_.ToString() }) -join "`n").Trim() }
}

function Get-BossEmulatorProcesses {
    $prefix = Join-Path $config.androidSdk 'emulator\'
    @(Get-CimInstance Win32_Process | Where-Object {
        $_.ExecutablePath -and $_.ExecutablePath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -and
        ($_.Name -eq 'emulator.exe' -or $_.Name -like 'qemu-system*') -and
        $_.CommandLine -match '(?:^|\s)-avd\s+"?PleosBoss"?(?:\s|$)'
    })
}

function Wait-BossDevice {
    $deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
    $started = Get-Date
    $nextMessage = Get-Date
    do {
        $state = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','get-state')
        if ($state.ExitCode -eq 0 -and $state.Text -eq 'device') {
            $boot = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','getprop','sys.boot_completed')
            if ($boot.ExitCode -eq 0 -and $boot.Text -eq '1') {
                $fingerprint = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','getprop','ro.build.fingerprint')
                if ($fingerprint.ExitCode -ne 0 -or $fingerprint.Text -notlike '*Pleos/crp_avd/*') {
                    throw '다른 기기가 연결되어 있습니다. 원정 보스용 Pleos 기기가 필요합니다.'
                }
                $currentUser = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','am','get-current-user')
                if ($currentUser.ExitCode -eq 0 -and $currentUser.Text -match '^\d+$' -and [int]$currentUser.Text -gt 0) {
                    return $currentUser.Text
                }
            }
        }
        if ((Get-Date) -gt $deadline) {
            throw '기기 준비 시간이 초과됐습니다. D:\Codex\PleosBossEmulator\emulator-error.log를 확인해 주세요.'
        }
        if ((Get-Date) -ge $nextMessage) {
            $elapsed = [int]((Get-Date) - $started).TotalSeconds
            Write-Host "기기를 준비하고 있습니다... $elapsed 초 (PC 상태에 따라 몇 분 걸릴 수 있습니다.)"
            $nextMessage = (Get-Date).AddSeconds(15)
        }
        if (((Get-Date) - $started).TotalSeconds -gt 12 -and @(Get-BossEmulatorProcesses).Count -eq 0) {
            throw '기기 창이 종료됐습니다. D:\Codex\PleosBossEmulator\emulator-error.log를 확인해 주세요.'
        }
        Start-Sleep -Seconds 2
    } while ($true)
}

$mutex = New-Object System.Threading.Mutex($false, 'Local\PleosBossLauncher')
$acquired = $false
$recording = $false
$failed = $false
try {
    try { $acquired = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $acquired = $true }
    if (-not $acquired) {
        Write-Host '이미 원정 보스를 실행하는 중입니다. 열린 기기 창을 잠시 기다려 주세요.'
        return
    }
    Start-Transcript -Path $log -Force | Out-Null
    $recording = $true
    Write-Host '원정 보스를 시작합니다.'
    if ($Mode -eq 'fixture') { & (Join-Path $PSScriptRoot 'start_social.ps1') }
    if (-not (Test-Path -LiteralPath $adb) -or -not (Test-Path -LiteralPath $apk)) {
        throw '실행 파일이 없습니다. setup.cmd를 실행해 주세요. SDK 시연 APK는 tools\download_apks.ps1 -Mode pleos로 받을 수 있습니다.'
    }
    $server = Invoke-BossAdb -AdbArguments @('start-server')
    if ($server.ExitCode -ne 0) { throw "기기 연결을 시작하지 못했습니다.`n$($server.Text)" }
    $device = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','get-state')
    $ownedProcesses = @(Get-BossEmulatorProcesses)
    $headless = @($ownedProcesses | Where-Object { $_.Name -like '*-headless.exe' -or $_.CommandLine -match '(?:^|\s)-no-window(?:\s|$)' })
    if ($headless.Count -gt 0) {
        Write-Host '화면 없이 켜진 시험 기기를 창이 보이는 상태로 다시 엽니다. 저장된 게임은 유지됩니다.'
        $null = Wait-BossDevice
        $stop = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','emu','kill')
        if ($stop.ExitCode -ne 0) { throw "시험 기기를 종료하지 못했습니다.`n$($stop.Text)" }
        $stopDeadline = (Get-Date).AddSeconds(45)
        while (@(Get-BossEmulatorProcesses).Count -gt 0) {
            if ((Get-Date) -gt $stopDeadline) { throw '시험 기기가 아직 종료 중입니다. 잠시 후 다시 실행해 주세요.' }
            Start-Sleep -Seconds 1
        }
        $device = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','get-state')
        $ownedProcesses = @()
    }
    if (($device.ExitCode -ne 0 -or $device.Text -ne 'device') -and $ownedProcesses.Count -eq 0) {
        & (Join-Path $PSScriptRoot 'start_emulator.ps1') -Visible -GpuMode $GpuMode
    }
    $driverUser = Wait-BossDevice
    Write-Host '기기가 준비됐습니다. 게임을 열고 있습니다...'
    $install = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','install','-r',$apk)
    if ($install.ExitCode -ne 0) { throw "게임을 설치하지 못했습니다.`n$($install.Text)" }
    $package = if ($Mode -eq 'fixture') { 'dev.pleos.entry.boss.fixture' } else { 'dev.pleos.entry.boss' }
    # Pleos may retain a hidden TaskView for an installed app. Recreate only our
    # app task, preserving Room data, and confirm its activity is actually resumed.
    $inFront = $false
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $stopApp = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','am','force-stop','--user',$driverUser,$package)
        if ($stopApp.ExitCode -ne 0) { throw "게임을 다시 열 준비를 하지 못했습니다.`n$($stopApp.Text)" }
        $launch = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','am','start','--user',$driverUser,'-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n',"$package/dev.pleos.entry.boss.MainActivity")
        if ($launch.ExitCode -ne 0 -or $launch.Text -match '(?m)^\s*(Error:|Exception|Failure)') {
            throw "게임 화면을 열지 못했습니다.`n$($launch.Text)"
        }
        Start-Sleep -Seconds 2
        $activities = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','dumpsys','activity','activities')
        $resumedPattern = 'topResumedActivity=.*' + [regex]::Escape($package) + '/'
        if ($activities.ExitCode -eq 0 -and $activities.Text -match $resumedPattern) {
            $inFront = $true
            break
        }
        Write-Host '게임 화면을 앞으로 가져오고 있습니다...'
    }
    if (-not $inFront) { throw '게임은 실행됐지만 기기 화면 앞에 표시되지 않았습니다.' }
    $pidResult = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','shell','pidof',$package)
    if ($pidResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($pidResult.Text)) { throw '게임이 시작 직후 종료됐습니다.' }
    if ($Mode -eq 'fixture') {
        Write-Host '게임이 열렸습니다. 기기 창에서 [7분 비움 · 10km 복귀]을 눌러 보세요.'
    } else {
        Write-Host '실제 SDK 연동용 게임 화면이 열렸습니다.'
        $forward = Invoke-BossAdb -AdbArguments @('-s','emulator-5554','forward','tcp:8088','tcp:8088')
        if ($forward.ExitCode -ne 0) { throw "차량 시험 콘솔을 연결하지 못했습니다.`n$($forward.Text)" }
        Write-Host '실제 SDK 시험의 가상 차량 콘솔: http://127.0.0.1:8088/'
    }
} catch {
    $failed = $true
    Write-Host "실행을 마치지 못했습니다: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "실행 기록: $log"
} finally {
    if ($recording) { Stop-Transcript | Out-Null }
    if ($acquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
if ($failed) { exit 1 }
