#requires -Version 7.4
param(
    [string]$SdkDirectory,
    [string]$JavaDirectory,
    [string]$PythonExecutable,
    [ValidateSet('Prompt','New','Existing')][string]$KeyMode='Prompt',
    [string]$KeystorePath,
    [string]$Alias='wealthos-release',
    [string]$ExpectedCertificateSha256,
    [string]$OutputApk,
    [string]$DistinguishedName='CN=WealthOS Personal Release',
    [Security.SecureString]$StorePassword,
    [Security.SecureString]$KeyPassword
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Release-Common.ps1"
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$candidate=Join-Path $repository '.tools/release-readiness/wealthos-release-unsigned.apk'
$record=Get-Content -LiteralPath (Join-Path $repository 'docs/verification/release-readiness-build.json') -Raw | ConvertFrom-Json
if((Get-FileHash -LiteralPath $candidate).Hash.ToLowerInvariant() -ne $record.unsignedApkSha256){throw 'The retained unsigned candidate differs from its verified build. Rebuild and review it before signing.'}
if(-not $SdkDirectory){
    $properties=Get-Content -LiteralPath (Join-Path $repository 'local.properties')
    $sdkLine=@($properties | Where-Object {$_ -match '^sdk\.dir='})
    if($sdkLine.Count -ne 1){throw 'Supply -SdkDirectory for the verified Android SDK.'}
    $SdkDirectory=$sdkLine[0].Substring(8).Replace('\:',':').Replace('\\','\')
}
if(-not $JavaDirectory){
    foreach($possible in @($env:JAVA_HOME,(Join-Path $env:ProgramFiles 'Android/Android Studio1/jbr'),(Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'))){
        if(-not $possible -or -not (Test-Path -LiteralPath (Join-Path $possible 'bin/java.exe'))){continue}
        try{$null=Get-ReleaseJava $possible;$JavaDirectory=$possible;break}catch{continue}
    }
    if(-not $JavaDirectory){throw 'Supply -JavaDirectory for Java 21.'}
}
$null=Get-ReleaseJava $JavaDirectory
if(-not $PythonExecutable){
    $pythonCommand=Get-Command python.exe -ErrorAction SilentlyContinue
    if($pythonCommand){$PythonExecutable=$pythonCommand.Source}
    elseif(Test-Path -LiteralPath 'C:/Python314/python.exe'){$PythonExecutable='C:/Python314/python.exe'}
    else{throw 'Supply -PythonExecutable for Python 3.11+.'}
}
$null=Invoke-ReleaseProcess $PythonExecutable @('-c','import sys; assert sys.version_info >= (3, 11)') -Operation 'Python compatibility check'
$null=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/lib/apksigner.jar'
$nodes=Get-ApkXmlNodes $SdkDirectory $candidate
if(@($nodes | Where-Object {$_.tag -eq 'manifest' -and $_.attributes['package'] -eq 'com.wealthos.app.release'}).Count -ne 1){throw 'Only the verified coexisting release can be signed with this setup.'}
if(-not $OutputApk){
    $outputDirectory=Join-Path $repository '.tools/production-release'
    $null=New-Item -ItemType Directory -Path $outputDirectory -Force
    $OutputApk=Join-Path $outputDirectory 'WealthOS-0.6.0.apk'
}
$OutputApk=Assert-NewReleasePath $OutputApk
$null=Assert-NewReleasePath ($OutputApk+'.verification.json')
Write-Host 'WealthOS Release setup: no phone access, installation or publication.'
Write-Host 'Private keys and passwords stay on this PC. Enter passphrases only in this local prompt.'
if($KeyMode -eq 'Prompt'){
    $choice=Read-Host '[N] Create a new permanent signing key, [E] use an existing key, [Q] cancel'
    $KeyMode=switch($choice.Trim().ToUpperInvariant()){'N'{'New'};'E'{'Existing'};default{throw 'Setup cancelled; no signing key created.'}}
}
if(-not $KeystorePath){
    if($KeyMode -eq 'New'){$KeystorePath=Join-Path $env:LOCALAPPDATA 'WealthOS/signing/wealthos-release.p12'}
    else{$KeystorePath=Read-Host 'Existing signing keystore absolute path (never send the private key in chat)'}
}
$KeystorePath=[IO.Path]::GetFullPath($KeystorePath)
$repositoryPrefix=$repository+[IO.Path]::DirectorySeparatorChar
if($KeystorePath.StartsWith($repositoryPrefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Keep the private signing key outside the repository.'}
if($KeyMode -eq 'New'){
    if(Test-Path -LiteralPath $KeystorePath){throw 'That keystore already exists. Choose Existing to use it; nothing will be overwritten.'}
    $keyDirectory=[IO.Path]::GetDirectoryName($KeystorePath)
    if(-not (Test-Path -LiteralPath $keyDirectory)){
        $directory=New-Item -ItemType Directory -Path $keyDirectory
        # Restrict this newly created directory before any private material is written.
        $acl=[Security.AccessControl.DirectorySecurity]::new()
        $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
        try{
            $acl.SetOwner($identity.User);$acl.SetAccessRuleProtection($true,$false)
            foreach($sid in @($identity.User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit, ObjectInherit','None','Allow'))}
            Set-Acl -LiteralPath $directory.FullName -AclObject $acl
        }finally{$identity.Dispose()}
    }
}else{
    if(-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)){throw 'The existing signing keystore was not found.'}
    if(-not $ExpectedCertificateSha256){$ExpectedCertificateSha256=Read-Host 'Known public certificate SHA-256 fingerprint'}
    $enteredAlias=Read-Host "Key alias (Enter for $Alias)"
    if($enteredAlias){$Alias=$enteredAlias}
}
$ownsPassword=$null -eq $StorePassword
$confirmation=$null;$a=$null;$b=$null
try{
    if($ownsPassword){$StorePassword=Read-Host 'Signing passphrase (new keys: 20–128 printable ASCII characters)' -AsSecureString}
    if($KeyMode -eq 'New'){
        if($ownsPassword){
            $confirmation=Read-Host 'Confirm new signing passphrase' -AsSecureString
            $a=Get-SecretCharacters $StorePassword;$b=Get-SecretCharacters $confirmation
            $difference=$a.Length -bxor $b.Length
            for($i=0;$i -lt [Math]::Min($a.Length,$b.Length);$i++){$difference=$difference -bor ([int]$a[$i] -bxor [int]$b[$i])}
            if($difference -ne 0){throw 'Signing passphrases do not match; no key created.'}
        }
        & "$PSScriptRoot/New-ReleaseKey.ps1" -JavaDirectory $JavaDirectory -KeystorePath $KeystorePath -Alias $Alias -DistinguishedName $DistinguishedName -Password $StorePassword
        $metadata=Get-Content -LiteralPath ($KeystorePath+'.public.json') -Raw | ConvertFrom-Json
        $ExpectedCertificateSha256=$metadata.certificateSha256
    }
    & "$PSScriptRoot/Sign-Release.ps1" -SdkDirectory $SdkDirectory -JavaDirectory $JavaDirectory -PythonExecutable $PythonExecutable -UnsignedApk $candidate -KeystorePath $KeystorePath -Alias $Alias -ExpectedCertificateSha256 $ExpectedCertificateSha256 -OutputApk $OutputApk -StorePassword $StorePassword -KeyPassword $KeyPassword
    Write-Host 'Retain an independently protected copy of the keystore and its passphrase before using this APK.'
    Write-Host 'The original app remains installed. Follow docs/RELEASE_SIGNING.md for reviewed backup-based migration.'
}finally{
    if($a){[Array]::Clear($a,0,$a.Length)};if($b){[Array]::Clear($b,0,$b.Length)}
    if($confirmation){$confirmation.Dispose()}
    if($ownsPassword -and $StorePassword){$StorePassword.Dispose()}
}
