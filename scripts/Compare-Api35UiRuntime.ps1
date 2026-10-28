#requires -Version 7.4
<# Replays existing tests only on the previously created fictional API 35 lab.
   The opt-in acknowledges resetting that lab's debug data; its separate restored
   release vault, other emulators and physical devices are never reset. #>
param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [Parameter(Mandatory=$true)][switch]$ResetReviewedSyntheticDebugFixture,
    [string[]]$Selectors=@()
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(-not $ResetReviewedSyntheticDebugFixture){throw 'Explicit synthetic debug-fixture reset acknowledgement required.'}
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb=Join-Path $SdkDirectory 'platform-tools/adb.exe'
$serial='emulator-5558'
function Invoke-LabCommand([string[]]$Arguments){
    $lines=& $adb -s $serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ('Lab command failed: '+($lines -join "`n"))}
    return ($lines -join "`n")
}
function Assert-Lab {
    if((((Invoke-LabCommand @('emu','avd','name')) -split "`r?`n")[0]).Trim() -ne 'WealthOS16K'){throw 'Wrong AVD.'}
    foreach($pair in @(@('ro.build.version.sdk','35'),@('sys.boot_completed','1'),@('ro.product.cpu.abi','x86_64'))){
        if((Invoke-LabCommand @('shell','getprop',$pair[0])).Trim() -ne $pair[1]){throw 'Unexpected runtime.'}
    }
    if((Invoke-LabCommand @('shell','getconf','PAGE_SIZE')).Trim() -ne '16384'){throw 'Expected 16 KiB userspace.'}
}
function Assert-InstalledHash([string]$Package,[string]$Expected){
    $location=(Invoke-LabCommand @('shell','pm','path',$Package)).Trim()
    if($location -notmatch '^package:(/data/app/[a-zA-Z0-9_~+=./-]+/base\.apk)$'){throw 'Unexpected package path.'}
    if((Invoke-LabCommand @('shell','sha256sum',$Matches[1])).Split(' ')[0] -ne $Expected){throw "Unexpected installed artifact: $Package"}
}
Assert-Lab
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $output){throw 'Preserve prior evidence; use a new output directory.'}
$appHash='71baccb417015beac48139fc2e4855c423ab46655d0e86ce720ece30c2fe5cd0'
$testHash='1f460f87881cdd2a678d8df8a90843a63d7e8883085b4cc0fdc8d46eba428332'
Assert-InstalledHash 'com.wealthos.app' $appHash
Assert-InstalledHash 'com.wealthos.app.release' '7b81b112ddcd3adfaec5c273f08ac436d808e99f1cb18dd505993c908de2af2d'
$testApk=Join-Path $repository 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
if((Get-FileHash -LiteralPath $testApk).Hash.ToLowerInvariant() -ne $testHash){throw 'Review changed UI harness before replaying.'}
[xml]$baseline=Get-Content -LiteralPath (Join-Path $repository 'docs/verification/milestone-6-app-api29.xml') -Raw
$inventory=@($baseline.SelectNodes('//testcase') | ForEach-Object {$_.classname+'#'+$_.name})
if($inventory.Count -ne 18 -or @($inventory|Select-Object -Unique).Count -ne 18){throw 'Unexpected baseline inventory.'}
if($Selectors.Count -eq 0){$Selectors=$inventory}
foreach($selector in $Selectors){if($selector -notin $inventory){throw 'Only original test selectors are allowed.'}}
$null=New-Item -ItemType Directory -Path $output
$null=Invoke-LabCommand @('install','-r',$testApk)
Assert-InstalledHash 'com.wealthos.app.test' $testHash
$settings=[ordered]@{}
foreach($name in @('window_animation_scale','transition_animation_scale','animator_duration_scale')){
    $settings[$name]=(Invoke-LabCommand @('shell','settings','get','global',$name)).Trim()
}
$results=[Collections.Generic.List[object]]::new()
try {
    foreach($name in $settings.Keys){$null=Invoke-LabCommand @('shell','settings','put','global',$name,'0')}
    $index=0
    foreach($selector in $Selectors){
        Assert-Lab
        $null=Invoke-LabCommand @('shell','am','force-stop','com.wealthos.app')
        if((Invoke-LabCommand @('shell','pm','clear','com.wealthos.app')).Trim() -ne 'Success'){throw 'Could not reset reviewed synthetic debug data.'}
        $index++;$log=Join-Path $output ('app-case-{0:D2}.log' -f $index)
        $started=[DateTimeOffset]::UtcNow
        Write-Output "Case ${index}/$($Selectors.Count): $selector"
        & $adb -s $serial shell am instrument -w -r -e disableAnalytics true -e class $selector com.wealthos.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Tee-Object -FilePath $log
        if($LASTEXITCODE -ne 0){throw 'Instrumentation transport failed.'}
        $raw=Get-Content -LiteralPath $log -Raw
        $passed=$raw -match 'OK \(1 test\)' -and [regex]::Matches($raw,'(?m)^INSTRUMENTATION_STATUS_CODE: 0\s*$').Count -eq 1 -and $raw -notmatch 'INSTRUMENTATION_STATUS_CODE: -[0-9]+|FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
        $results.Add([ordered]@{selector=$selector;passed=$passed;startedUtc=$started.ToString('o');durationSeconds=[Math]::Round(([DateTimeOffset]::UtcNow-$started).TotalSeconds,3);logSha256=(Get-FileHash -LiteralPath $log).Hash.ToLowerInvariant()})
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'cases.json') -Encoding utf8NoBOM
        if(-not $passed){Write-Warning 'Original expected result did not pass; retained as a failure.'}
    }
} finally {
    foreach($name in $settings.Keys){
        if($settings[$name] -eq 'null'){$null=Invoke-LabCommand @('shell','settings','delete','global',$name)}
        else {$null=Invoke-LabCommand @('shell','settings','put','global',$name,$settings[$name])}
        if((Invoke-LabCommand @('shell','settings','get','global',$name)).Trim() -ne $settings[$name]){throw 'Animation setting restoration failed.'}
    }
    $settings | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'restored-settings.json') -Encoding utf8NoBOM
    Invoke-LabCommand @('shell','dumpsys','activity','exit-info','com.wealthos.app') | Set-Content -LiteralPath (Join-Path $output 'debug-exit-info.txt')
}
Assert-Lab
$failures=@($results | Where-Object {-not $_['passed']}).Count
[ordered]@{date=[DateTimeOffset]::UtcNow.ToString('o');avd='WealthOS16K';serial=$serial;api=35;pageSize=16384;emulatorBootSerial=(Invoke-LabCommand @('shell','getprop','ro.boot.serialno')).Trim();fingerprint=(Invoke-LabCommand @('shell','getprop','ro.build.fingerprint')).Trim();appSha256=$appHash;testApkSha256=$testHash;cases=$results;passed=$results.Count-$failures;failures=$failures;skipped=0;phoneAccessed=$false;releaseVaultReset=$false} | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $output 'report.json') -Encoding utf8NoBOM
if($failures){throw "$failures case(s) failed. No expectations were changed."}
Write-Output "PASS: $($results.Count) existing cases; expected outputs unchanged."
