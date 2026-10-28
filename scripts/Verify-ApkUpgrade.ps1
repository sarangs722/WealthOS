param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$OriginalApk,
    [string]$Serial = 'emulator-5554',
    [string]$UpdatedApk = 'app/build/outputs/apk/debug/app-debug.apk',
    [string]$TestApk = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
    [string]$OutputDirectory = '.tools/m6-upgrade-verification',
    [switch]$ResumeRetention
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'This fixture only runs on an isolated emulator, never a physical device.' }
$upgradeAdb = Join-Path $SdkDirectory 'platform-tools/adb.exe'
foreach ($upgradeFile in @($upgradeAdb,$OriginalApk,$UpdatedApk,$TestApk)) {
    if (-not (Test-Path -LiteralPath $upgradeFile -PathType Leaf)) { throw "Missing fixture input: $upgradeFile" }
}
function Invoke-UpgradeAdb([string[]]$Arguments) {
    $upgradeOutput = & $upgradeAdb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($upgradeOutput -join "`n") }
    return ($upgradeOutput -join "`n")
}
$upgradeAvd = Invoke-UpgradeAdb -Arguments @('emu','avd','name')
$upgradeExpected = @{'emulator-5554'=@('WealthOS','36');'emulator-5556'=@('WealthOS29','29')}
if (-not $upgradeExpected.ContainsKey($Serial) -or ($upgradeAvd -split "`r?`n")[0] -ne $upgradeExpected[$Serial][0]) { throw 'Use the explicitly named isolated WealthOS AVD for this fixture.' }
if ((Invoke-UpgradeAdb -Arguments @('shell','getprop','ro.build.version.sdk')).Trim() -ne $upgradeExpected[$Serial][1]) { throw 'Unexpected Android API.' }
if ((Invoke-UpgradeAdb -Arguments @('shell','getprop','sys.boot_completed')).Trim() -ne '1') { throw 'Wait for emulator boot.' }
$upgradeExisting = Invoke-UpgradeAdb -Arguments @('shell','pm','list','packages','com.wealthos.app')
$upgradeInstalled = ($upgradeExisting -split "`r?`n") -contains 'package:com.wealthos.app'
if ($upgradeInstalled -and -not $ResumeRetention) { throw 'The fixture requires an empty test AVD. Existing app data will not be cleared or uninstalled by this script.' }
if ($ResumeRetention -and (-not $upgradeInstalled -or -not (Test-Path -LiteralPath (Join-Path $OutputDirectory 'old-apk-seed.txt')))) { throw 'Resume requires the existing isolated old-APK fixture and its seed evidence.' }
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$upgradeBuildTools = Join-Path $SdkDirectory 'build-tools/36.1.0/apksigner.bat'
function Get-UpgradeSigner([string]$Apk) {
    $upgradeSigning = & $upgradeBuildTools verify --print-certs $Apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $upgradeMatch = [regex]::Match(($upgradeSigning -join "`n"),'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)')
    if (-not $upgradeMatch.Success) { throw 'Could not read signing certificate digest.' }
    return $upgradeMatch.Groups[1].Value.ToLowerInvariant()
}
$upgradeOriginalSigner = Get-UpgradeSigner $OriginalApk
$upgradeNewSigner = Get-UpgradeSigner $UpdatedApk
if ($upgradeOriginalSigner -ne $upgradeNewSigner) { throw 'The new APK is not signed by the existing signing identity.' }
if (-not $ResumeRetention) {
    Write-Output (Invoke-UpgradeAdb -Arguments @('install','-r',$OriginalApk))
    Write-Output (Invoke-UpgradeAdb -Arguments @('install','-r',$TestApk))
    $upgradeSeed = Invoke-UpgradeAdb -Arguments @('shell','am','instrument','-w','-e','class','com.wealthos.app.UpgradeSeedTest','-e','seedOldApk','true','com.wealthos.app.test/androidx.test.runner.AndroidJUnitRunner')
    $upgradeSeed | Set-Content -LiteralPath (Join-Path $OutputDirectory 'old-apk-seed.txt')
} else {
    $upgradeSeed = Get-Content -LiteralPath (Join-Path $OutputDirectory 'old-apk-seed.txt') -Raw
    Write-Output (Invoke-UpgradeAdb -Arguments @('install','-r',$TestApk))
}
if ($upgradeSeed -notmatch 'OK \(1 test\)') { throw "Old APK fixture failed:`n$upgradeSeed" }
$upgradeName = (Invoke-UpgradeAdb -Arguments @('exec-out','run-as','com.wealthos.app','cat','files/upgrade-account.txt')).Trim()
if ($upgradeName -notmatch '^Upgrade bank [0-9]+$') { throw 'Unexpected fixture label; refusing to use non-synthetic account information.' }
Write-Output (Invoke-UpgradeAdb -Arguments @('install','-r',$UpdatedApk))
$upgradeCheck = Invoke-UpgradeAdb -Arguments @('shell','am','instrument','-w','-e','class','com.wealthos.app.UpgradeRetentionTest','-e','upgradeAccount',("'"+$upgradeName+"'"),'com.wealthos.app.test/androidx.test.runner.AndroidJUnitRunner')
$upgradeCheck | Set-Content -LiteralPath (Join-Path $OutputDirectory 'updated-apk-retention.txt')
if ($upgradeCheck -notmatch 'OK \(1 test\)') { throw "Updated APK retention fixture failed:`n$upgradeCheck" }

# Raw PNG streaming avoids shell/text encoding corruption. The test only captures explicitly fictional demo data.
$upgradeProcess = [System.Diagnostics.Process]::new()
$upgradeProcess.StartInfo.FileName = $upgradeAdb
$upgradeProcess.StartInfo.UseShellExecute = $false
$upgradeProcess.StartInfo.CreateNoWindow = $true
$upgradeProcess.StartInfo.RedirectStandardOutput = $true
foreach ($upgradeArgument in @('-s',$Serial,'exec-out','run-as','com.wealthos.app','cat','files/m6-demo-backups.png')) { $upgradeProcess.StartInfo.ArgumentList.Add($upgradeArgument) }
$upgradeProcess.Start() | Out-Null
$upgradeImage = [IO.File]::Create((Join-Path (Resolve-Path $OutputDirectory) 'm6-demo-backups.png'))
try { $upgradeProcess.StandardOutput.BaseStream.CopyTo($upgradeImage) } finally { $upgradeImage.Dispose() }
$upgradeProcess.WaitForExit()
if ($upgradeProcess.ExitCode -ne 0) { throw 'Could not retrieve fictional rendering fixture.' }
$upgradeLaunch = Invoke-UpgradeAdb -Arguments @('shell','am','start','-W','-n','com.wealthos.app/.MainActivity')
if ($upgradeLaunch -notmatch 'Status: ok') { throw "Updated launcher failed: $upgradeLaunch" }
$upgradeSummary = [ordered]@{
    originalApkSha256=(Get-FileHash -LiteralPath $OriginalApk -Algorithm SHA256).Hash.ToLowerInvariant()
    updatedApkSha256=(Get-FileHash -LiteralPath $UpdatedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    signerSha256=$upgradeNewSigner
    seed='OK (1 test)'
    retainedBankHistory='OK (1 test)'
    retainedOpeningHoldingAndCost='OK (1 test)'
    retainedIncomeHistory='OK (1 test)'
    retainedSavedForecast='OK (1 test)'
    optionalNetworkDefaultsOff='OK (1 test)'
    launcher='Status: ok'
    serial=$Serial
    avd=$upgradeExpected[$Serial][0]
    api=$upgradeExpected[$Serial][1]
}
$upgradeSummary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'result.json')
Write-Output ($upgradeSummary | ConvertTo-Json)
