param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$JavaDirectory,
    [string]$Serial = 'emulator-5554',
    [switch]$ResetSyntheticFixture
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Regression fixtures require an isolated emulator, never a personal phone.' }
$regressionAdb = Join-Path $SdkDirectory 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $regressionAdb -PathType Leaf)) { throw 'Android platform tools were not found.' }
if (-not (Test-Path -LiteralPath (Join-Path $JavaDirectory 'bin/java.exe') -PathType Leaf)) { throw 'The Java runtime was not found.' }
$regressionAvd = & $regressionAdb -s $Serial emu avd name 2>&1
$regressionExpected = @{'emulator-5554'=@('WealthOS','36');'emulator-5556'=@('WealthOS29','29')}
if (-not $regressionExpected.ContainsKey($Serial) -or $LASTEXITCODE -ne 0 -or ($regressionAvd -split "`r?`n")[0] -ne $regressionExpected[$Serial][0]) { throw 'Start the explicitly named isolated WealthOS AVD first.' }
$regressionApi = (& $regressionAdb -s $Serial shell getprop ro.build.version.sdk).Trim()
if ($LASTEXITCODE -ne 0 -or $regressionApi -ne $regressionExpected[$Serial][1]) { throw 'Unexpected emulator Android API.' }
$regressionBoot = (& $regressionAdb -s $Serial shell getprop sys.boot_completed).Trim()
if ($LASTEXITCODE -ne 0 -or $regressionBoot -ne '1') { throw 'Wait until the isolated WealthOS emulator has finished booting before running tests.' }
if ($ResetSyntheticFixture) {
    # Explicit opt-in; never used for an in-place upgrade verification or a physical phone.
    $regressionPackages = & $regressionAdb -s $Serial shell pm list packages com.wealthos.app
    if ($LASTEXITCODE -ne 0) { throw 'Package service is unavailable; synthetic fixture reset did not run.' }
    foreach ($regressionPackage in @('com.wealthos.app.test','com.wealthos.app')) {
        if ($regressionPackages -contains "package:$regressionPackage") {
            & $regressionAdb -s $Serial uninstall $regressionPackage
            if ($LASTEXITCODE -ne 0) { throw 'Could not remove the synthetic emulator fixture.' }
        }
    }
}
$regressionOldSerial=$env:ANDROID_SERIAL
$regressionOldSdk=$env:ANDROID_HOME
$regressionOldRoot=$env:ANDROID_SDK_ROOT
$regressionOldJava=$env:JAVA_HOME
function Assert-DeviceReport([string]$Module,[int]$Minimum,[datetime]$Started) {
    $regressionReport = Join-Path "$PSScriptRoot/.." "$Module/build/reports/androidTests/connected/debug/index.html"
    if (-not (Test-Path -LiteralPath $regressionReport) -or (Get-Item -LiteralPath $regressionReport).LastWriteTime -lt $Started) { throw "No fresh instrumentation report for $Module." }
    $regressionHtml = Get-Content -LiteralPath $regressionReport -Raw
    $regressionCounts = @{}
    foreach ($regressionMetric in @('tests','failures','skipped')) {
        $regressionMatch = [regex]::Match($regressionHtml,('id="'+$regressionMetric+'">\s*<div class="counter">([0-9]+)</div>'))
        if (-not $regressionMatch.Success) { throw "Unrecognized instrumentation report for $Module." }
        $regressionCounts[$regressionMetric] = [int]$regressionMatch.Groups[1].Value
    }
    if ($regressionCounts.tests -lt $Minimum -or $regressionCounts.failures -ne 0 -or $regressionCounts.skipped -ne 0) { throw "Incomplete/failed instrumentation for ${Module}: $($regressionCounts | ConvertTo-Json -Compress)" }
    Write-Output "Verified ${Module}: $($regressionCounts.tests) executed tests, no failures/skips."
}
try {
    $env:ANDROID_SERIAL=$Serial
    $env:ANDROID_HOME=$SdkDirectory
    $env:ANDROID_SDK_ROOT=$SdkDirectory
    $env:JAVA_HOME=$JavaDirectory
    # Separate invocations prevent two instrumentation engines from sharing one emulator concurrently.
    $regressionStarted = Get-Date
    & "$PSScriptRoot/../gradlew.bat" :core:finance:test :core:security:test :core:network:testDebugUnitTest :core:network:connectedDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Domain/public-data verification failed; inspect the Gradle reports.' }
    Assert-DeviceReport 'core/network' 7 $regressionStarted
    $regressionStarted = Get-Date
    & "$PSScriptRoot/../gradlew.bat" :core:database:connectedDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Domain/database verification failed; inspect the Gradle reports.' }
    Assert-DeviceReport 'core/database' 58 $regressionStarted
    $regressionStarted = Get-Date
    & "$PSScriptRoot/../gradlew.bat" :app:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Regression/build verification failed; inspect the Gradle reports.' }
    Assert-DeviceReport 'app' 37 $regressionStarted
} finally {
    $env:ANDROID_SERIAL=$regressionOldSerial
    $env:ANDROID_HOME=$regressionOldSdk
    $env:ANDROID_SDK_ROOT=$regressionOldRoot
    $env:JAVA_HOME=$regressionOldJava
}
