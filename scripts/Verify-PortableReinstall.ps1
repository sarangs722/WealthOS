param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [ValidateSet('emulator-5554','emulator-5556')][string]$Serial='emulator-5556',
    [string]$Apk='app/build/outputs/apk/debug/app-debug.apk',
    [string]$TestApk='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
    [string]$OutputDirectory='.tools/m6-portable-reinstall'
)
$ErrorActionPreference='Stop'
$portableAdb=Join-Path $SdkDirectory 'platform-tools/adb.exe'
foreach($portableFile in @($portableAdb,$Apk,$TestApk)){if(-not (Test-Path -LiteralPath $portableFile -PathType Leaf)){throw "Missing fixture input: $portableFile"}}
function Invoke-PortableAdb([string[]]$Arguments){
    $portableOutput=& $portableAdb -s $Serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($portableOutput -join "`n")}
    return ($portableOutput -join "`n")
}
$portableExpected=@{'emulator-5554'=@('WealthOS','36');'emulator-5556'=@('WealthOS29','29')}
$portableAvd=Invoke-PortableAdb @('emu','avd','name')
if(($portableAvd -split "`r?`n")[0] -ne $portableExpected[$Serial][0]){throw 'Wrong AVD; no fixture will run.'}
if((Invoke-PortableAdb @('shell','getprop','ro.build.version.sdk')).Trim() -ne $portableExpected[$Serial][1]){throw 'Wrong emulator API.'}
if((Invoke-PortableAdb @('shell','getprop','sys.boot_completed')).Trim() -ne '1'){throw 'Wait for emulator boot.'}
if(((Invoke-PortableAdb @('shell','pm','list','packages','com.wealthos.app')) -split "`r?`n") -contains 'package:com.wealthos.app'){throw 'This destructive reinstall fixture requires an empty test AVD. Existing app data will not be cleared.'}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Choose a new output directory to retain previous fixture evidence.'}
New-Item -ItemType Directory $OutputDirectory | Out-Null
Write-Output (Invoke-PortableAdb @('install',$Apk))
Write-Output (Invoke-PortableAdb @('install','-r',$TestApk))
$portableSeed=Invoke-PortableAdb @('shell','am','instrument','-w','-e','class','com.wealthos.app.PortableReinstallTest','-e','portablePhase','seed','com.wealthos.app.test/androidx.test.runner.AndroidJUnitRunner')
$portableSeed | Set-Content (Join-Path $OutputDirectory 'seed.txt')
if($portableSeed -notmatch 'OK \(1 test\)'){throw "Synthetic export fixture failed: $portableSeed"}
$portableOldKey=(Invoke-PortableAdb @('shell','run-as','com.wealthos.app','sha256sum','no_backup/database-key.v1')).Split(' ')[0]
if($portableOldKey -notmatch '^[0-9a-f]{64}$'){throw 'Could not fingerprint the synthetic wrapped key.'}
# Stream only the explicitly named ENCRYPTED fictional backup, without shell text conversion.
$portableProcess=[System.Diagnostics.Process]::new()
$portableProcess.StartInfo.FileName=$portableAdb
$portableProcess.StartInfo.UseShellExecute=$false
$portableProcess.StartInfo.CreateNoWindow=$true
$portableProcess.StartInfo.RedirectStandardOutput=$true
foreach($portableArgument in @('-s',$Serial,'exec-out','run-as','com.wealthos.app','cat','files/portable-fixture.v1.wosbackup')){$portableProcess.StartInfo.ArgumentList.Add($portableArgument)}
$portableArchive=Join-Path (Resolve-Path $OutputDirectory) 'fictional-portable.wosbackup'
$portableProcess.Start() | Out-Null
$portableStream=[IO.File]::Create($portableArchive)
try{$portableProcess.StandardOutput.BaseStream.CopyTo($portableStream)}finally{$portableStream.Dispose()}
$portableProcess.WaitForExit()
if($portableProcess.ExitCode -ne 0){throw 'Could not archive synthetic backup; app was not uninstalled.'}
$portableBytes=[IO.File]::ReadAllBytes($portableArchive)
if($portableBytes.Length -le 88 -or [Text.Encoding]::ASCII.GetString($portableBytes,0,8) -ne 'WOSBAK01'){throw 'Invalid fixture archive; refusing to uninstall.'}
# Only after a successful synthetic seed/export and archive: uninstall that just-created test installation.
Write-Output (Invoke-PortableAdb @('uninstall','com.wealthos.app'))
Write-Output (Invoke-PortableAdb @('install',$Apk))
Write-Output (Invoke-PortableAdb @('install','-r',$TestApk))
Write-Output (Invoke-PortableAdb @('shell','am','start','-W','-n','com.wealthos.app/.MainActivity'))
Write-Output (Invoke-PortableAdb @('push',$portableArchive,'/data/local/tmp/wealthos-portable-fixture.wosbackup'))
Write-Output (Invoke-PortableAdb @('shell','run-as','com.wealthos.app','mkdir','-p','files'))
Write-Output (Invoke-PortableAdb @('shell','run-as','com.wealthos.app','cp','/data/local/tmp/wealthos-portable-fixture.wosbackup','files/portable-fixture.v1.wosbackup'))
$portableRestore=Invoke-PortableAdb @('shell','am','instrument','-w','-e','class','com.wealthos.app.PortableReinstallTest','-e','portablePhase','restore','com.wealthos.app.test/androidx.test.runner.AndroidJUnitRunner')
$portableRestore | Set-Content (Join-Path $OutputDirectory 'restore.txt')
if($portableRestore -notmatch 'OK \(1 test\)'){throw "Actual reinstall/restore fixture failed: $portableRestore"}
$portableNewKey=(Invoke-PortableAdb @('shell','run-as','com.wealthos.app','sha256sum','no_backup/database-key.v1')).Split(' ')[0]
if($portableNewKey -notmatch '^[0-9a-f]{64}$' -or $portableOldKey -eq $portableNewKey){throw 'Fresh protected key was not independently verified.'}
Write-Output (Invoke-PortableAdb @('shell','rm','/data/local/tmp/wealthos-portable-fixture.wosbackup'))
$portableResult=[ordered]@{serial=$Serial;avd=$portableExpected[$Serial][0];api=$portableExpected[$Serial][1];apkSha256=(Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant();encryptedArchiveSha256=(Get-FileHash -LiteralPath $portableArchive -Algorithm SHA256).Hash.ToLowerInvariant();oldWrappedKeySha256=$portableOldKey;newWrappedKeySha256=$portableNewKey;seed='OK (1 test)';actualUninstallAndReinstall=$true;restore='OK (1 test)';retained='Bank revisions, decimal balances, FIFO opening units/cost, NAV, net worth';network='off';safety='retained'}
$portableResult | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'result.json')
Write-Output ($portableResult | ConvertTo-Json)
