param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
try {
    if (-not ([IO.Path]::GetFullPath($root).StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase))) {
        throw '압축을 D:\Codex 아래에 푼 뒤 setup.cmd를 실행해 주세요. 자동 설치는 D드라이브를 사용합니다.'
    }
    . (Join-Path $PSScriptRoot 'storage.ps1')
    $toolchain = if ($env:PLEOS_BOSS_TOOLCHAIN) { $env:PLEOS_BOSS_TOOLCHAIN } else { 'D:\Codex\PleosBossToolchain' }
    Set-BossStoragePaths -ToolchainRoot $toolchain
    $python = Get-Command py -ErrorAction SilentlyContinue
    $pythonArgs = @('-3')
    if (-not $python) {
        $python = Get-Command python -ErrorAction SilentlyContinue
        $pythonArgs = @()
    }
    if (-not $python) { throw 'Python 3.11 이상이 필요합니다. https://www.python.org/downloads/windows/ 에서 설치한 후 다시 실행해 주세요.' }
    & $python.Source @pythonArgs -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)'
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.11 이상을 설치해 주세요.' }
    Write-Host '원정 보스 실행 환경을 준비합니다. 처음에는 공식 기기 이미지 등 대용량 파일을 내려받습니다.'
    Write-Host '기존 개발 도구가 있으면 재사용합니다. 다운로드·설치에는 인터넷 연결이 필요합니다.'
    & $python.Source @pythonArgs (Join-Path $PSScriptRoot 'bootstrap_android.py') --pleos-image
    if ($LASTEXITCODE -ne 0) { throw '개발 도구 준비에 실패했습니다. 위 메시지와 개발 도구 폴더의 sdk-install.log를 확인해 주세요.' }
    & (Join-Path $PSScriptRoot 'download_apks.ps1') -Mode fixture
    Write-Host '준비가 끝났습니다. 다음부터는 demo.cmd로 실행할 수 있습니다.'
    if (-not $PrepareOnly) { & (Join-Path $PSScriptRoot 'run_demo.ps1') -Mode fixture }
} catch {
    Write-Host "설치 실패: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
