#requires -Version 7.4
param(
    [Parameter(Mandatory=$true)][string]$SdkDirectory,
    [Parameter(Mandatory=$true)][string]$JavaDirectory,
    [Parameter(Mandatory=$true)][string]$PythonExecutable,
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$DevelopmentSignedApk,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$scripts=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. "$scripts/Release-Common.ps1"
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $output){throw 'Use a new output directory for each test run.'}
$null=New-Item -ItemType Directory -Path $output
$temporaryRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$keyDirectory=Join-Path $temporaryRoot ('WealthOS-signing-fixture-'+[guid]::NewGuid().ToString())
$null=New-Item -ItemType Directory -Path $keyDirectory
$key=Join-Path $keyDirectory 'fixture.p12'
$fixture=Join-Path $output 'TEST-ONLY-disposable-signer.apk'
$password=[Security.SecureString]::new()
# Exercise spaces and shell metacharacters without ever invoking a shell or printing the secret.
foreach($character in ([guid]::NewGuid().ToString('N')+' $`"!&%').ToCharArray()){$password.AppendChar($character)}
$password.MakeReadOnly()
$wrongPassword=ConvertTo-SecureString ('wrong-'+[guid]::NewGuid().ToString('N')) -AsPlainText -Force
$checks=[Collections.Generic.List[object]]::new()
function Assert-Check([string]$Name,[bool]$Condition){if(-not $Condition){throw "FAILED: $Name"};$checks.Add(@{name=$Name;result='passed'});Write-Output "PASS: $Name"}
function Assert-Rejected([string]$Name,[scriptblock]$Action,[string]$ExpectedMessage){
    $rejected=$false
    try{& $Action | Out-Null}catch{if($_.Exception.Message -notmatch $ExpectedMessage){throw};$rejected=$true}
    Assert-Check $Name $rejected
}
$original=(Get-FileHash -LiteralPath $UnsignedApk).Hash
$common=@{SdkDirectory=$SdkDirectory;JavaDirectory=$JavaDirectory;PythonExecutable=$PythonExecutable}
try{
    $unicode=ConvertTo-SecureString '₹漢🙂' -AsPlainText -Force
    $decoded=$null
    try{$decoded=Get-SecretCharacters $unicode;Assert-Check 'Existing-key password buffers preserve UTF-16 code units' ([string]::new($decoded) -ceq '₹漢🙂')}finally{if($decoded){[Array]::Clear($decoded,0,$decoded.Length)};$unicode.Dispose()}
    $newline=ConvertTo-SecureString "fixture`npassword" -AsPlainText -Force
    try{Assert-Rejected 'Newline password cannot alter signer input framing' {Get-SecretCharacters $newline} 'cannot contain a newline'}finally{$newline.Dispose()}
    & "$scripts/New-ReleaseKey.ps1" -JavaDirectory $JavaDirectory -KeystorePath $key -Alias 'wealthos-fixture' -DistinguishedName 'CN=WealthOS Signing Test Fixture' -Password $password | Out-Null
    $metadata=Get-Content -LiteralPath ($key+'.public.json') -Raw | ConvertFrom-Json
    $fingerprint=$metadata.certificateSha256
    $keyHash=(Get-FileHash -LiteralPath $key).Hash
    Assert-Check 'Encrypted fixture key and public certificate created outside repository' ((Test-Path -LiteralPath ($key+'.cer')) -and $metadata.bits -eq 4096 -and $fingerprint.Length -eq 64)
    Assert-Check 'Password environment variable never added to parent process' ($null -eq [Environment]::GetEnvironmentVariable('WEALTHOS_KEYTOOL_PASSWORD','Process'))
    Assert-Rejected 'Existing keystore cannot be overwritten' {& "$scripts/New-ReleaseKey.ps1" -JavaDirectory $JavaDirectory -KeystorePath $key -Password $password} 'destination already exists'
    Assert-Rejected 'Repository keystore path rejected' {& "$scripts/New-ReleaseKey.ps1" -JavaDirectory $JavaDirectory -KeystorePath (Join-Path $output 'forbidden.p12') -Password $password} 'outside the repository'
    $sign=@{}+$common+@{UnsignedApk=$UnsignedApk;KeystorePath=$key;Alias='wealthos-fixture';ExpectedCertificateSha256=$fingerprint;StorePassword=$password}
    & "$scripts/Sign-Release.ps1" @sign -OutputApk $fixture | Out-Null
    $verified=Get-Content -LiteralPath ($fixture+'.verification.json') -Raw | ConvertFrom-Json
    Assert-Check 'Actual shrunk release signs and passes full package signature backup and native checks' ($verified.status -eq 'PACKAGE_AND_SIGNATURE_VERIFIED' -and $verified.applicationId -eq 'com.wealthos.app.release' -and $verified.certificateSha256 -eq $fingerprint)
    Assert-Rejected 'Wrong public certificate pin rejected' {& "$scripts/Verify-ReleaseCandidate.ps1" @common -Apk $fixture -ExpectedCertificateSha256 ('0'*64)} 'does not match'
    Assert-Rejected 'Development certificate rejected on actual minified APK' {& "$scripts/Verify-ReleaseCandidate.ps1" @common -Apk $DevelopmentSignedApk -ExpectedCertificateSha256 'dd7d62a4506fd0cd64cb1dddaa11a56f838694a64b7ec8c6f79f6461c5589f73' -ExpectedApplicationId 'com.wealthos.app'} 'development signing identity'
    $wrong=@{}+$sign;$wrong.StorePassword=$wrongPassword
    $badOutput=Join-Path $output 'wrong-password.apk'
    Assert-Rejected 'Incorrect keystore password fails closed' {& "$scripts/Sign-Release.ps1" @wrong -OutputApk $badOutput} 'signing failed'
    Assert-Check 'Failed signing publishes no APK or report' (-not (Test-Path -LiteralPath $badOutput) -and -not (Test-Path -LiteralPath ($badOutput+'.verification.json')))
    $badPin=@{}+$sign;$badPin.ExpectedCertificateSha256='0'*64
    $badPinOutput=Join-Path $output 'wrong-pin.apk'
    Assert-Rejected 'Signed output with wrong pin never accepted' {& "$scripts/Sign-Release.ps1" @badPin -OutputApk $badPinOutput} 'does not match'
    Assert-Check 'Failed verification removes only its staging outputs' (-not (Test-Path -LiteralPath $badPinOutput) -and @(Get-ChildItem -LiteralPath $output -Filter '.wealthos-signed-*').Count -eq 0)
    $acceptedHash=(Get-FileHash -LiteralPath $fixture).Hash
    Assert-Rejected 'Existing accepted APK cannot be overwritten' {& "$scripts/Sign-Release.ps1" @sign -OutputApk $fixture} 'destination already exists'
    Assert-Check 'Accepted APK remains byte identical' ((Get-FileHash -LiteralPath $fixture).Hash -eq $acceptedHash)
    $tampered=Join-Path $output 'TEST-ONLY-tampered.apk'
    Copy-Item -LiteralPath $fixture -Destination $tampered
    $archive=[IO.Compression.ZipFile]::Open($tampered,[IO.Compression.ZipArchiveMode]::Update)
    try{$entry=$archive.CreateEntry('assets/signing-test-tamper.txt');$writer=[IO.StreamWriter]::new($entry.Open());try{$writer.Write('Deliberate synthetic signature tampering.')}finally{$writer.Dispose()}}finally{$archive.Dispose()}
    Assert-Rejected 'Modified signed APK rejected cryptographically' {& "$scripts/Verify-ReleaseCandidate.ps1" @common -Apk $tampered -ExpectedCertificateSha256 $fingerprint} 'signature verification failed'
    Assert-Check 'Unsigned source and encrypted keystore remain byte identical' ((Get-FileHash -LiteralPath $UnsignedApk).Hash -eq $original -and (Get-FileHash -LiteralPath $key).Hash -eq $keyHash)
    Assert-Check 'No accidental repository key or failed accepted artifact' (-not (Test-Path -LiteralPath (Join-Path $output 'forbidden.p12')) -and -not (Test-Path -LiteralPath $badPinOutput))
    $result=[ordered]@{scope='Local signing tools; disposable test identity, never a production release';passed=$checks.Count;checks=$checks;fixtureCertificateSha256=$fingerprint;fixtureApkSha256=$acceptedHash.ToLowerInvariant();unsignedApkSha256=$original.ToLowerInvariant();phoneAccessed=$false;productionKeyCreated=$false;fixturePrivateKeyRetained=$false}
}finally{
    $password.Dispose();$wrongPassword.Dispose()
    # Remove only the exact owned fixture files, then the empty owned directory.
    if(-not ([IO.Path]::GetFullPath($keyDirectory).StartsWith($temporaryRoot,[StringComparison]::OrdinalIgnoreCase)) -or [IO.Path]::GetFileName($keyDirectory) -notlike 'WealthOS-signing-fixture-*'){throw 'Unexpected fixture cleanup path.'}
    foreach($owned in @($key,($key+'.cer'),($key+'.public.json'))){if(Test-Path -LiteralPath $owned){Remove-Item -LiteralPath $owned}}
    Remove-Item -LiteralPath $keyDirectory
}
Assert-Check 'Disposable private keystore and its temporary directory removed' (-not (Test-Path -LiteralPath $keyDirectory))
$result.passed=$checks.Count
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'tests.json') -Encoding utf8NoBOM
# Expected negative cases invoke SDK processes that return nonzero. The suite succeeds
# only after all assertions and cleanup; do not leak their exit codes to the caller.
exit 0
