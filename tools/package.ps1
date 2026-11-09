$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain-state.json') -Raw | ConvertFrom-Json
$build = Join-Path $config.toolchainRoot 'build'
$destination = Join-Path $root 'artifacts'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$manifest = @()
foreach ($flavor in @('fixture','pleos')) {
    $source = Join-Path $build "app-boss\outputs\apk\$flavor\debug\app-boss-$flavor-debug.apk"
    $output = Join-Path $destination "boss-$flavor-debug.apk"
    Copy-Item -LiteralPath $source -Destination $output
    $manifest += [ordered]@{file=[IO.Path]::GetFileName($output); bytes=(Get-Item -LiteralPath $output).Length; sha256=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$report = Join-Path $root 'docs\validation'
Copy-Item -LiteralPath (Join-Path $build 'core-game\test-results\test\TEST-dev.pleos.boss.game.GameTest.xml') -Destination $report
[xml]$testXml = Get-Content -LiteralPath (Join-Path $report 'TEST-dev.pleos.boss.game.GameTest.xml') -Raw
[ordered]@{
    suite = [string]$testXml.testsuite.name
    tests = [int]$testXml.testsuite.tests
    failures = [int]$testXml.testsuite.failures
    errors = [int]$testXml.testsuite.errors
    skipped = [int]$testXml.testsuite.skipped
    timestamp = [string]$testXml.testsuite.timestamp
    testCases = @($testXml.testsuite.testcase | ForEach-Object { [string]$_.name })
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $report 'unit-tests.json') -Encoding UTF8
[ordered]@{
    packagedAt=(Get-Date -Format o)
    naviHelper='3.0.5'
    compileSdk=35
    minimumSdk=33
    buildType='debug'
    artifacts=$manifest
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'build-manifest.json') -Encoding utf8
Write-Host "Packaged APKs: $destination"
