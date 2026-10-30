#requires -Version 7.4
param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$JavaDirectory,
    [Parameter(Mandatory=$true)][string]$PythonExecutable,
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$ExpectedCertificateSha256,
    [ValidateSet('com.wealthos.app.release','com.wealthos.app')][string]$ExpectedApplicationId='com.wealthos.app.release',
    [string]$ReportPath
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Release-Common.ps1"
. "$PSScriptRoot/Release-Components.ps1"
$java=Get-ReleaseJava $JavaDirectory
$signer=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/lib/apksigner.jar'
$zipalign=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/zipalign.exe'
$aapt=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/aapt2.exe'
$apkPath=(Resolve-Path -LiteralPath $Apk).Path
$expected=$ExpectedCertificateSha256.Replace(':','').ToLowerInvariant()
if($expected -notmatch '^[a-f0-9]{64}$'){throw 'Provide the public signing certificate SHA-256 fingerprint (64 hex digits).'}
if($ReportPath){$ReportPath=Assert-NewReleasePath $ReportPath}
$before=(Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$signature=Invoke-ReleaseProcess $java @('-jar',$signer,'verify','--verbose','--print-certs','--print-certs-pem',$apkPath) -Operation 'APK signature verification'
$digest=[regex]::Match($signature,'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})')
if(-not $digest.Success -or $digest.Groups[1].Value.ToLowerInvariant() -ne $expected){throw 'The APK signing certificate does not match the expected public fingerprint.'}
if($signature -notmatch 'Number of signers: 1\b' -or $signature -notmatch 'Verified using v3 scheme \(APK Signature Scheme v3\): true'){throw 'Expected one signer and a verified APK v3 signature for Android 10+.'}
if($signature -match '(?i)CN=Android\s*Debug' -or $expected -eq 'dd7d62a4506fd0cd64cb1dddaa11a56f838694a64b7ec8c6f79f6461c5589f73'){throw 'An Android development signing identity is not accepted as a production release key.'}
$nodes=Get-ApkXmlNodes $SdkDirectory $apkPath
$manifest=@($nodes | Where-Object tag -eq 'manifest')
$application=@($nodes | Where-Object tag -eq 'application')
$sdk=@($nodes | Where-Object tag -eq 'uses-sdk')
if($manifest.Count -ne 1 -or $application.Count -ne 1 -or $sdk.Count -ne 1){throw 'Unrecognized application manifest.'}
$attributes=$application[0].attributes
if($manifest[0].attributes['package'] -ne $ExpectedApplicationId){throw 'The APK application ID does not match the requested release identity.'}
if($attributes['debuggable'] -eq 'true' -or $attributes['testOnly'] -eq 'true'){throw 'Debuggable or test-only APKs are not accepted.'}
foreach($flag in @('allowBackup','usesCleartextTraffic','extractNativeLibs')){if($attributes[$flag] -ne 'false'){throw "Required release flag is missing or unsafe: $flag"}}
if($sdk[0].attributes['minSdkVersion'] -ne '29' -or $sdk[0].attributes['targetSdkVersion'] -ne '37'){throw 'Unexpected supported Android SDK range.'}
if($ExpectedApplicationId -eq 'com.wealthos.app.release' -and $attributes['label'] -ne 'WealthOS Release'){throw 'The coexisting release must have a distinct launcher label.'}
$permissions=@($nodes | Where-Object tag -eq 'uses-permission' | ForEach-Object {$_.attributes['name']} | Sort-Object)
$allowed=@('android.permission.INTERNET','android.permission.USE_BIOMETRIC',"$ExpectedApplicationId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") | Sort-Object
if(@(Compare-Object $permissions $allowed).Count -ne 0){throw 'The release permission set changed and requires review.'}
Assert-ReleaseComponents -Nodes $nodes -ApplicationId $ExpectedApplicationId -VersionCode ([int]$manifest[0].attributes['versionCode'])
$resources=Invoke-ReleaseProcess $aapt @('dump','resources',$apkPath) -Operation 'Backup resource inspection'
foreach($pair in @(@('fullBackupContent','full-backup-content'),@('dataExtractionRules','data-extraction-rules'))){
    $reference=$attributes[$pair[0]]
    if($reference -notmatch '^@(0x[0-9a-f]+)$'){throw 'Explicit backup/extraction rules are missing.'}
    $resourceId=$Matches[1]
    $file=[regex]::Match($resources,('(?m)^\s*resource '+[regex]::Escape($resourceId)+' [^\r\n]+\r?\n\s*\(\) \(file\) (\S+) type=XML'))
    if(-not $file.Success){throw 'Cannot resolve the packaged backup/extraction XML.'}
    $rules=Get-ApkXmlNodes $SdkDirectory $apkPath $file.Groups[1].Value
    $parents=if($pair[1] -eq 'full-backup-content'){@('full-backup-content')}else{@('cloud-backup','device-transfer')}
    foreach($parent in $parents){
        $exclusions=@($rules | Where-Object {$_.tag -eq 'exclude' -and $_.parent -eq $parent -and $_.attributes['path'] -eq '.'} | ForEach-Object {$_.attributes['domain']} | Sort-Object)
        if(@(Compare-Object $exclusions (@('root','database','sharedpref','file','external') | Sort-Object)).Count -ne 0){throw 'Financial files are not excluded from every packaged OS backup/transfer scope.'}
    }
}
$null=Invoke-ReleaseProcess $zipalign @('-c','-P','16','4',$apkPath) -Operation 'APK ZIP alignment verification'
$native=Invoke-ReleaseProcess $PythonExecutable @((Join-Path $PSScriptRoot 'Verify-ApkPackaging.py'),$apkPath) -Operation 'Native ELF alignment verification'
$nativeReport=$native | ConvertFrom-Json
if([int]$manifest[0].attributes['versionCode'] -ge 15){
    $null=Invoke-ReleaseProcess $PythonExecutable @((Join-Path $PSScriptRoot 'Verify-StatementPackaging.py'),$apkPath) -Operation 'Packaged financial schema verification'
}
$after=(Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if($before -ne $after -or $nativeReport.sha256 -ne $after){throw 'The APK changed during verification.'}
$report=[ordered]@{status='PACKAGE_AND_SIGNATURE_VERIFIED';applicationId=$ExpectedApplicationId;versionName=$manifest[0].attributes['versionName'];versionCode=[int]$manifest[0].attributes['versionCode'];apkSha256=$after;certificateSha256=$expected;minSdk=29;targetSdk=37;debuggable=$false;testOnly=$false;osBackupExcluded=$true;cleartext=$false;permissions=$permissions;componentInventory='reviewed';nativeLibraries=$nativeReport.libraries.Count;elfAndZip16KiB='passed';phoneAccessed=$false;physicalDeviceReview='pending';independentSecurityReview='pending'}
$json=$report | ConvertTo-Json -Depth 5
if($ReportPath){$stream=[IO.File]::Open($ReportPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($json+"`n");$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}}
Write-Output $json
