param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [string]$Serial='emulator-5554',
    [string]$TestApk='core/network/build/outputs/apk/androidTest/debug/network-debug-androidTest.apk',
    [string]$OutputDirectory='.tools/m5-live-verification'
)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-[0-9]+$'){throw 'Public-feed verification requires the isolated test emulator.'}
$publicAdb=Join-Path $SdkDirectory 'platform-tools/adb.exe'
if(-not(Test-Path -LiteralPath $TestApk -PathType Leaf)){throw 'Build the public-data Android test APK first.'}
$publicAvd=& $publicAdb -s $Serial emu avd name 2>&1
if($LASTEXITCODE -ne 0 -or ($publicAvd -split "`r?`n")[0] -ne 'WealthOS'){throw 'Use the isolated WealthOS AVD.'}
if((& $publicAdb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1'){throw 'Wait until the emulator finishes booting.'}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
& $publicAdb -s $Serial install -r $TestApk
if($LASTEXITCODE -ne 0){throw 'Could not install the public-data test fixture.'}
$publicResult=& $publicAdb -s $Serial shell am instrument -w -e class com.wealthos.publicdata.LivePublicFeedsTest -e livePublic true com.wealthos.publicdata.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$publicExit=$LASTEXITCODE
$publicResult | Set-Content -LiteralPath (Join-Path $OutputDirectory 'official-https-feeds.txt')
if($publicExit -ne 0 -or ($publicResult -join "`n") -notmatch 'OK \(3 tests\)'){throw "Live provider verification failed; inspect $OutputDirectory/official-https-feeds.txt. Cache/offline behavior is tested independently."}
Write-Output ($publicResult -join "`n")
