#requires -Version 7.4
param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$JavaDirectory,
    [Parameter(Mandatory=$true)][string]$PythonExecutable,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$scripts=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$setup=Join-Path $scripts 'Complete-ReleaseSetup.ps1'
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $output){throw 'Use a new fixture output directory.'}
$null=New-Item -ItemType Directory -Path $output
$temporaryRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixtureDirectory=Join-Path $temporaryRoot ('WealthOS-setup-fixture-'+[guid]::NewGuid().ToString())
$key=Join-Path $fixtureDirectory 'setup-test.p12'
$password=ConvertTo-SecureString ([guid]::NewGuid().ToString('N')+' !&$') -AsPlainText -Force
$checks=[Collections.Generic.List[object]]::new()
function Assert-Check([string]$Name,[bool]$Condition){if(-not $Condition){throw "FAILED: $Name"};$checks.Add(@{name=$Name;result='passed'});Write-Output "PASS: $Name"}
function Assert-Rejected([string]$Name,[scriptblock]$Action,[string]$Message){$rejected=$false;try{& $Action | Out-Null}catch{if($_.Exception.Message -notmatch $Message){throw};$rejected=$true};Assert-Check $Name $rejected}
# Existing-key setup only asks for the public alias. Secrets are supplied as
# SecureString fixture inputs, never read from a command line or report.
function Read-Host {param([string]$Prompt,[switch]$AsSecureString);if($AsSecureString){throw 'Unexpected secret prompt in fixture'};return ''}
$common=@{SdkDirectory=$SdkDirectory;JavaDirectory=$JavaDirectory;PythonExecutable=$PythonExecutable;KeystorePath=$key;Alias='wealthos-setup-fixture';StorePassword=$password}
$first=Join-Path $output 'TEST-ONLY-setup-new.apk'
$second=Join-Path $output 'TEST-ONLY-setup-existing.apk'
try{
    & $setup @common -KeyMode New -DistinguishedName 'CN=WealthOS Setup Test Fixture' -OutputApk $first | Out-Null
    $metadata=Get-Content -LiteralPath ($key+'.public.json') -Raw | ConvertFrom-Json
    $verified=Get-Content -LiteralPath ($first+'.verification.json') -Raw | ConvertFrom-Json
    Assert-Check 'One-command new-key setup signs and verifies retained candidate' ($verified.status -eq 'PACKAGE_AND_SIGNATURE_VERIFIED' -and $verified.certificateSha256 -eq $metadata.certificateSha256)
    $acl=Get-Acl -LiteralPath $fixtureDirectory
    $rules=$acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])
    $user=[Security.Principal.WindowsIdentity]::GetCurrent()
    try{$allowed=@($user.User.Value,'S-1-5-18');Assert-Check 'New key directory grants only current user and SYSTEM with inheritance disabled' ($acl.AreAccessRulesProtected -and @($rules).Count -eq 2 -and @($rules | Where-Object {$_.IdentityReference.Value -notin $allowed -or $_.IsInherited}).Count -eq 0)}finally{$user.Dispose()}
    $keyHash=(Get-FileHash -LiteralPath $key).Hash
    & $setup @common -KeyMode Existing -ExpectedCertificateSha256 $metadata.certificateSha256 -OutputApk $second | Out-Null
    $reused=Get-Content -LiteralPath ($second+'.verification.json') -Raw | ConvertFrom-Json
    Assert-Check 'Existing-key setup retains same certificate and coexisting package identity' ($reused.certificateSha256 -eq $metadata.certificateSha256 -and $reused.applicationId -eq 'com.wealthos.app.release')
    Assert-Check 'Existing private keystore remains byte identical' ((Get-FileHash -LiteralPath $key).Hash -eq $keyHash)
    Assert-Rejected 'Existing destination refused before key access' {& $setup @common -KeyMode Existing -ExpectedCertificateSha256 $metadata.certificateSha256 -OutputApk $first} 'destination already exists'
    $bad=Join-Path $output 'wrong-pin.apk'
    Assert-Rejected 'Wrong existing-key certificate pin fails closed' {& $setup @common -KeyMode Existing -ExpectedCertificateSha256 ('0'*64) -OutputApk $bad} 'does not match'
    Assert-Check 'Wrong pin publishes no accepted artifact or staging output' (-not (Test-Path -LiteralPath $bad) -and @(Get-ChildItem -LiteralPath $output -Filter '.wealthos-signed-*').Count -eq 0)
    Assert-Check 'No signing password enters parent environment' ($null -eq [Environment]::GetEnvironmentVariable('WEALTHOS_KEYTOOL_PASSWORD','Process'))
    $result=[ordered]@{scope='One-command local setup, disposable identity only';passed=0;checks=$checks;fixtureCertificateSha256=$metadata.certificateSha256;newSetupApkSha256=$verified.apkSha256;existingSetupApkSha256=$reused.apkSha256;productionKeyCreated=$false;fixturePrivateKeyRetained=$false;phoneAccessed=$false}
}finally{
    $password.Dispose()
    if(-not ([IO.Path]::GetFullPath($fixtureDirectory).StartsWith($temporaryRoot,[StringComparison]::OrdinalIgnoreCase)) -or [IO.Path]::GetFileName($fixtureDirectory) -notlike 'WealthOS-setup-fixture-*'){throw 'Unexpected cleanup path'}
    foreach($owned in @($key,($key+'.cer'),($key+'.public.json'))){if(Test-Path -LiteralPath $owned){Remove-Item -LiteralPath $owned}}
    if(Test-Path -LiteralPath $fixtureDirectory){Remove-Item -LiteralPath $fixtureDirectory}
}
Assert-Check 'Disposable setup private key removed after tests' (-not (Test-Path -LiteralPath $fixtureDirectory))
$result.passed=$checks.Count
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'tests.json') -Encoding utf8NoBOM
exit 0
