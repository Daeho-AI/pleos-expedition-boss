function Set-BossStoragePaths {
    param([Parameter(Mandatory=$true)][string]$ToolchainRoot)
    $toolRoot = [IO.Path]::GetFullPath($ToolchainRoot)
    if (-not $toolRoot.StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'This project stores development files on D:. Update toolchain-state.json.'
    }
    $paths = @{
        TEMP = 'D:\Codex\.tmp'
        TMP = 'D:\Codex\.tmp'
        TMPDIR = 'D:\Codex\.tmp'
        ANDROID_USER_HOME = (Join-Path $toolRoot 'android-user')
        ANDROID_EMULATOR_HOME = 'D:\Codex\PleosBossEmulator'
        ANDROID_AVD_HOME = 'D:\Codex\PleosBossEmulator\avd'
    }
    foreach ($entry in $paths.GetEnumerator()) {
        New-Item -ItemType Directory -Path $entry.Value -Force | Out-Null
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
