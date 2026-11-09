param([switch]$Visible, [string]$DnsServers = '8.8.8.8,8.8.4.4')
$ErrorActionPreference = 'Stop'
$s = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain-state.json') -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'storage.ps1')
Set-BossStoragePaths -ToolchainRoot $s.toolchainRoot
$emuRoot = 'D:\Codex\PleosBossEmulator'
$avdHome = Join-Path $emuRoot 'avd'
$avdPath = Join-Path $avdHome 'PleosBoss.avd'
New-Item -ItemType Directory -Force -Path $avdPath | Out-Null
$image = Join-Path $s.androidSdk 'system-images\android-34\pleos_car_emulator\crp_avd_out_x86_64'
if (-not (Test-Path -LiteralPath (Join-Path $image 'system.img'))) { throw 'Install Pleos image first.' }
$ini = "avd.ini.encoding=UTF-8`npath=$avdPath`ntarget=android-34`n"
[IO.File]::WriteAllText((Join-Path $avdHome 'PleosBoss.ini'), $ini)
$config = @"
avd.ini.encoding=UTF-8
AvdId=PleosBoss
avd.ini.displayname=Pleos Boss Test
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=4096
hw.lcd.width=2560
hw.lcd.height=1440
hw.lcd.density=160
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.gps=yes
hw.audioInput=yes
hw.mainKeys=no
hw.device.name=Pleos_Connect_v2_0
hw.device.manufacturer=42dot
disk.dataPartition.size=4G
image.sysdir.1=$image
tag.id=android-automotive
tag.display=Pleos Connect v2.1.0
target=android-34
fastboot.forceColdBoot=yes
"@
[IO.File]::WriteAllText((Join-Path $avdPath 'config.ini'), $config)
$env:ANDROID_AVD_HOME = $avdHome
$env:ANDROID_HOME = $s.androidSdk
$env:ANDROID_SDK_ROOT = $s.androidSdk
$arguments = @('-avd','PleosBoss','-no-snapshot','-no-audio','-port','5554')
# Explicit DNS applies only to this emulator; Windows network settings are unchanged.
if ($DnsServers) { $arguments += @('-dns-server', $DnsServers) }
if (-not $Visible) { $arguments += @('-no-window','-no-boot-anim') }
$style = if ($Visible) { 'Normal' } else { 'Hidden' }
$process = Start-Process -FilePath (Join-Path $s.androidSdk 'emulator\emulator.exe') -ArgumentList $arguments -WindowStyle $style -PassThru -RedirectStandardOutput (Join-Path $emuRoot 'emulator.log') -RedirectStandardError (Join-Path $emuRoot 'emulator-error.log')
Write-Output "Pleos emulator PID=$($process.Id); log=$emuRoot"
