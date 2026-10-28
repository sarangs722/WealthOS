#requires -Version 7.4
param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$JavaDirectory,
    [Parameter(Mandatory=$true)][string]$PythonExecutable,
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$KeystorePath,
    [Parameter(Mandatory=$true)][string]$Alias,
    [Parameter(Mandatory=$true)][string]$ExpectedCertificateSha256,
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [ValidateSet('com.wealthos.app.release','com.wealthos.app')][string]$ExpectedApplicationId='com.wealthos.app.release',
    [switch]$AllowMainPackageIdentity,
    [Security.SecureString]$StorePassword,
    [Security.SecureString]$KeyPassword
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Release-Common.ps1"
$java=Get-ReleaseJava $JavaDirectory
$signer=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/lib/apksigner.jar'
$zipalign=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/zipalign.exe'
$inputApk=(Resolve-Path -LiteralPath $UnsignedApk).Path
$keyPath=(Resolve-Path -LiteralPath $KeystorePath).Path
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))+[IO.Path]::DirectorySeparatorChar
if($keyPath.StartsWith($repository,[StringComparison]::OrdinalIgnoreCase)){throw 'Move the signing keystore outside the repository before using it.'}
if([IO.Path]::GetFileName($keyPath) -eq 'debug.keystore'){throw 'The Android debug keystore is not a production signing identity.'}
$output=Assert-NewReleasePath $OutputApk
$report=Assert-NewReleasePath ($output+'.verification.json')
if($ExpectedCertificateSha256.Replace(':','') -notmatch '^[0-9a-fA-F]{64}$'){throw 'Provide the expected public certificate SHA-256 fingerprint.'}
if($ExpectedApplicationId -eq 'com.wealthos.app' -and -not $AllowMainPackageIdentity){throw 'A new production certificate cannot update the development app. Build with -Pwealthos.coexistingRelease=true, or explicitly select an established main-package production identity.'}
$nodes=Get-ApkXmlNodes $SdkDirectory $inputApk
$manifest=@($nodes | Where-Object tag -eq 'manifest')
if($manifest.Count -ne 1 -or $manifest[0].attributes['package'] -ne $ExpectedApplicationId){throw 'Build the requested release application ID before signing.'}
$null=Invoke-ReleaseProcess $zipalign @('-c','-P','16','4',$inputApk) -Operation 'Unsigned APK ZIP alignment verification'
$ownsStore=$null -eq $StorePassword
if($ownsStore){$StorePassword=Read-Host 'Signing keystore password (not the app backup password)' -AsSecureString}
if($null -eq $KeyPassword){$KeyPassword=$StorePassword}
$temporary=Join-Path ([IO.Path]::GetDirectoryName($output)) ('.wealthos-signed-'+[guid]::NewGuid().ToString()+'.apk')
$temporaryReport=$temporary+'.verification.json'
try{
    $null=Invoke-ReleaseProcess $java @('-jar',$signer,'sign','--ks',$keyPath,'--ks-key-alias',$Alias,'--ks-pass','stdin','--key-pass','stdin','--pass-encoding','utf-8','--v4-signing-enabled','false','--out',$temporary,$inputApk) -Passwords @($StorePassword,$KeyPassword) -Operation 'Release APK signing'
    $verification=& "$PSScriptRoot/Verify-ReleaseCandidate.ps1" -SdkDirectory $SdkDirectory -JavaDirectory $JavaDirectory -PythonExecutable $PythonExecutable -Apk $temporary -ExpectedCertificateSha256 $ExpectedCertificateSha256 -ExpectedApplicationId $ExpectedApplicationId -ReportPath $temporaryReport
    # Publish only a completely verified candidate, without replacing any existing file.
    [IO.File]::Move($temporary,$output,$false)
    [IO.File]::Move($temporaryReport,$report,$false)
    Write-Output "Signed APK: $output"
    Write-Output 'Package/signature checks passed. Physical-device and independent security review remain separate release requirements.'
    Write-Output $verification
}finally{
    if($ownsStore -and $StorePassword){$StorePassword.Dispose()}
    foreach($ownedFile in @($temporary,$temporaryReport)){if(Test-Path -LiteralPath $ownedFile){Remove-Item -LiteralPath $ownedFile}}
}
