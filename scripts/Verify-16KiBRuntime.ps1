#requires -Version 7.4
param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [ValidateSet('emulator-5558','emulator-5560')][string]$Serial='emulator-5558',
    [switch]$ResumeBeforeAppTests,
    [ValidateSet('database','network','app')][string[]]$Suites=@('database','network','app'),
    [switch]$IsolateAppTests
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb=Join-Path $SdkDirectory 'platform-tools/adb.exe'
$serial=$Serial
$runtime=@{'emulator-5558'=@('WealthOS16K','35');'emulator-5560'=@('WealthOS16K36','36')}[$serial]
function Invoke-TestAdb([string[]]$Arguments){
    $lines=& $adb -s $serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ('Test emulator command failed: '+($lines -join "`n"))}
    return ($lines -join "`n")
}
function Assert-TestRuntime {
    if((((Invoke-TestAdb @('emu','avd','name')) -split "`r?`n")[0]).Trim() -ne $runtime[0]){throw 'Only the explicitly named 16 KiB test emulator is permitted.'}
    if((Invoke-TestAdb @('shell','getprop','ro.build.version.sdk')).Trim() -ne $runtime[1]){throw 'Unexpected emulator API.'}
    if((Invoke-TestAdb @('shell','getprop','sys.boot_completed')).Trim() -ne '1'){throw 'Wait for test emulator boot.'}
    if((Invoke-TestAdb @('shell','getconf','PAGE_SIZE')).Trim() -ne '16384'){throw 'The emulator does not expose 16 KiB pages.'}
    if((Invoke-TestAdb @('shell','getprop','ro.product.cpu.abi')).Trim() -ne 'x86_64'){throw 'Unexpected test ABI; review the image/runtime before testing.'}
}
Assert-TestRuntime
$installed=@((Invoke-TestAdb @('shell','pm','list','packages','com.wealthos.')) -split "`r?`n" | ForEach-Object {$_.Trim()} | Where-Object {$_})
if($installed.Count -gt 0){
    if(-not $ResumeBeforeAppTests){throw 'Use a fresh named test AVD, or explicitly resume before app tests; this script never resets an existing fixture.'}
    $allowed=@('package:com.wealthos.app','package:com.wealthos.database.test','package:com.wealthos.publicdata.test','package:com.wealthos.app.test')
    if(@($installed | Where-Object {$_ -notin $allowed}).Count){throw 'Unexpected existing WealthOS package.'}
    if('package:com.wealthos.app' -in $installed){
        $appState=Invoke-TestAdb @('shell','dumpsys','package','com.wealthos.app')
        if($appState -notmatch 'User 0: [^\r\n]*notLaunched=true'){throw 'The app has already been used; preserve that fixture and use a fresh AVD.'}
    }
}
function Install-TestArtifact([string]$Apk,[string]$Package){
    if(('package:'+$Package) -in $installed){
        $location=(Invoke-TestAdb @('shell','pm','path',$Package)).Trim()
        if($location -notmatch '^package:(/data/app/[a-zA-Z0-9_~+=./-]+/base\.apk)$'){throw 'Unexpected installed APK location.'}
        $digest=(Invoke-TestAdb @('shell','sha256sum',$Matches[1])).Split(' ')[0]
        if($digest -ne (Get-FileHash -LiteralPath $Apk).Hash.ToLowerInvariant()){throw 'An installed fixture APK differs from the selected test artifact.'}
        Write-Output "Preserved byte-identical installed fixture: $Package"
    }else{$null=Invoke-TestAdb @('install',$Apk)}
}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $output){throw 'Use a new verification output directory.'}
$null=New-Item -ItemType Directory -Path $output
$artifacts=@(
    @{name='database';file='core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk';package='com.wealthos.database.test';tests=58},
    @{name='network';file='core/network/build/outputs/apk/androidTest/debug/network-debug-androidTest.apk';package='com.wealthos.publicdata.test';tests=7},
    @{name='app';file='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk';package='com.wealthos.app.test';tests=18}
)
$artifacts=@($artifacts | Where-Object {$_.name -in $Suites})
$results=[Collections.Generic.List[object]]::new()
foreach($artifact in $artifacts){if(-not (Test-Path -LiteralPath (Join-Path $repository $artifact.file) -PathType Leaf)){throw 'Build all test APKs before starting verification.'}}
$app=Join-Path $repository 'app/build/outputs/apk/debug/app-debug.apk'
if((Get-FileHash -LiteralPath $app).Hash.ToLowerInvariant() -ne '71baccb417015beac48139fc2e4855c423ab46655d0e86ce720ece30c2fe5cd0'){throw 'The development APK differs from the verified 0.6.0 fixture; review the new artifact before running.'}
Install-TestArtifact $app 'com.wealthos.app'
foreach($artifact in $artifacts){
    Assert-TestRuntime
    $apk=Join-Path $repository $artifact.file
    Install-TestArtifact $apk $artifact.package
    $log=Join-Path $output ($artifact.name+'.log')
    $started=[DateTimeOffset]::UtcNow
    $caseResults=[Collections.Generic.List[object]]::new()
    Write-Output "Running $($artifact.name) on $serial / $($runtime[0]), expected $($artifact.tests) tests."
    # Address the exact serial directly: no connected-device discovery or personal phone access.
    if($artifact.name -eq 'app' -and $IsolateAppTests){
        # Same isolation principle as Android Test Orchestrator: each case gets a
        # new process and empty synthetic data. Only the guarded fresh test AVD
        # and reviewed development APK can reach this path.
        [xml]$baseline=Get-Content -LiteralPath (Join-Path $repository 'docs/verification/milestone-6-app-api29.xml') -Raw
        $cases=@($baseline.SelectNodes('//testcase'))
        if($cases.Count -ne 18 -or @($cases | ForEach-Object {$_.classname+'#'+$_.name} | Select-Object -Unique).Count -ne 18){throw 'Review the app case inventory before changing the fixture.'}
        $index=0
        foreach($case in $cases){
            $selector=$case.classname+'#'+$case.name
            if($selector -notmatch '^com\.wealthos\.app\.[A-Za-z0-9_]+#[A-Za-z0-9_]+$'){throw 'Unsupported instrumentation selector.'}
            Assert-TestRuntime
            $null=Invoke-TestAdb @('shell','am','force-stop','com.wealthos.app')
            if((Invoke-TestAdb @('shell','pm','clear','com.wealthos.app')).Trim() -ne 'Success'){throw 'Could not initialize the isolated synthetic case.'}
            $index++;$caseLog=Join-Path $output ('app-case-{0:D2}.log' -f $index)
            Write-Output "Isolated app case ${index}/18: $selector"
            & $adb -s $serial shell am instrument -w -r -e disableAnalytics true -e class $selector ($artifact.package+'/androidx.test.runner.AndroidJUnitRunner') 2>&1 | Tee-Object -FilePath $caseLog
            if($LASTEXITCODE -ne 0){throw 'Isolated instrumentation command failed.'}
            $caseText=Get-Content -LiteralPath $caseLog -Raw
            [IO.File]::AppendAllText($log,$caseText+"`n",[Text.UTF8Encoding]::new($false))
            $casePassed=$caseText -match 'OK \(1 test\)' -and [regex]::Matches($caseText,'(?m)^INSTRUMENTATION_STATUS_CODE: 0\s*$').Count -eq 1 -and $caseText -notmatch 'INSTRUMENTATION_STATUS_CODE: -[0-9]+|FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
            $caseResults.Add(@{selector=$selector;passed=[int]$casePassed;failures=[int](-not $casePassed);skipped=0})
            $caseResults | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'isolated-case-results.json') -Encoding utf8NoBOM
            if(-not $casePassed){Write-Warning "Failed isolated case retained for diagnosis: $selector. Remaining cases will still execute; this suite cannot pass."}
        }
    }else{
        & $adb -s $serial shell am instrument -w -r -e disableAnalytics true ($artifact.package+'/androidx.test.runner.AndroidJUnitRunner') 2>&1 | Tee-Object -FilePath $log
        if($LASTEXITCODE -ne 0){throw "Instrumentation command failed: $($artifact.name)"}
    }
    $text=Get-Content -LiteralPath $log -Raw
    $summary=[regex]::Match($text,'OK \(([0-9]+) tests?\)')
    $passed=[regex]::Matches($text,'(?m)^INSTRUMENTATION_STATUS_CODE: 0\s*$').Count
    $summaryCount=if($artifact.name -eq 'app' -and $IsolateAppTests){$caseResults.Count}else{if($summary.Success){[int]$summary.Groups[1].Value}else{0}}
    if(-not $summary.Success -or $summaryCount -ne $artifact.tests -or $passed -ne $artifact.tests -or $text -match 'INSTRUMENTATION_STATUS_CODE: -[0-9]+|FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'){
        throw "Incomplete, skipped or failed $($artifact.name) suite. Inspect $log; expected results were not changed."
    }
    $results.Add([ordered]@{suite=$artifact.name;passed=$passed;failures=0;skipped=0;apkSha256=(Get-FileHash -LiteralPath $apk).Hash.ToLowerInvariant();isolatedCases=$caseResults;startedUtc=$started.ToString('o');durationSeconds=[Math]::Round(([DateTimeOffset]::UtcNow-$started).TotalSeconds,3)})
    $results | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output 'completed-suites.json') -Encoding utf8NoBOM
}
Assert-TestRuntime
$totalPassed=0
foreach($completed in $results){$totalPassed += [int]$completed['passed']}
$report=[ordered]@{date='2026-10-28';serial=$serial;avd=$runtime[0];api=[int]$runtime[1];abi='x86_64';userspacePageSize=16384;runtimeScope='Official Google 16 KiB x86_64 userspace simulation; not physical ARM hardware';fingerprint=(Invoke-TestAdb @('shell','getprop','ro.build.fingerprint')).Trim();appVersion='0.6.0';appApkSha256=(Get-FileHash -LiteralPath $app).Hash.ToLowerInvariant();testsPassed=$totalPassed;suites=$results;physicalDeviceVerified=$false;phoneAccessed=$false;existingEmulatorsModified=$false}
$report | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath (Join-Path $output 'runtime-tests.json') -Encoding utf8NoBOM
Write-Output "PASS: $($report.testsPassed) tests executed with 16 KiB userspace pages, no failures/skips."
