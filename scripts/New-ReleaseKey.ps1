#requires -Version 7.4
param(
    [Parameter(Mandatory=$true)][string]$JavaDirectory,
    [Parameter(Mandatory=$true)][string]$KeystorePath,
    [string]$Alias='wealthos-release',
    [string]$DistinguishedName='CN=WealthOS Personal Release',
    [Security.SecureString]$Password
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Release-Common.ps1"
$null=Get-ReleaseJava $JavaDirectory
$keytool=Get-ReleaseTool $JavaDirectory 'bin/keytool.exe'
$destination=Assert-NewReleasePath $KeystorePath
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))+[IO.Path]::DirectorySeparatorChar
if($destination.StartsWith($repository,[StringComparison]::OrdinalIgnoreCase)){throw 'Keep the production keystore outside the repository and its build/cache folders.'}
if([IO.Path]::GetExtension($destination) -ne '.p12'){throw 'New release keys use the PKCS12 .p12 format.'}
if($Alias -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$'){throw 'Use a simple non-secret key alias (up to 80 characters).'}
$certificatePath=Assert-NewReleasePath ($destination+'.cer')
$metadataPath=Assert-NewReleasePath ($destination+'.public.json')
$ownsPassword=$null -eq $Password
if($ownsPassword){
    $Password=Read-Host 'New signing passphrase (20–128 printable ASCII characters)' -AsSecureString
    $confirmation=Read-Host 'Confirm signing passphrase' -AsSecureString
    $a=$null;$b=$null
    try{
        $a=Get-SecretCharacters $Password;$b=Get-SecretCharacters $confirmation
        $difference=$a.Length -bxor $b.Length
        for($i=0;$i -lt [Math]::Min($a.Length,$b.Length);$i++){$difference=$difference -bor ([int]$a[$i] -bxor [int]$b[$i])}
        if($difference -ne 0){$Password.Dispose();throw 'Signing passphrases do not match.'}
    }finally{if($a){[Array]::Clear($a,0,$a.Length)};if($b){[Array]::Clear($b,0,$b.Length)};$confirmation.Dispose()}
}
$directory=[IO.Path]::GetDirectoryName($destination)
$temporary=Join-Path $directory ('.wealthos-key-'+[guid]::NewGuid().ToString()+'.p12')
$temporaryCertificate=$temporary+'.cer'
$characters=$null
try{
    $characters=Get-SecretCharacters $Password
    if($characters.Length -lt 20 -or $characters.Length -gt 128 -or @($characters | Where-Object {[int]$_ -lt 32 -or [int]$_ -gt 126}).Count -gt 0){throw 'Use 20–128 printable ASCII characters for the signing keystore. This constraint does not apply to app backup passwords.'}
    [Array]::Clear($characters,0,$characters.Length);$characters=$null
    $secret=@{'WEALTHOS_KEYTOOL_PASSWORD'=$Password}
    $null=Invoke-ReleaseProcess $keytool @('-genkeypair','-storetype','PKCS12','-keystore',$temporary,'-alias',$Alias,'-keyalg','RSA','-keysize','4096','-sigalg','SHA256withRSA','-validity','10950','-dname',$DistinguishedName,'-storepass:env','WEALTHOS_KEYTOOL_PASSWORD','-keypass:env','WEALTHOS_KEYTOOL_PASSWORD','-noprompt') -SecretEnvironment $secret -Operation 'Release key creation'
    $null=Invoke-ReleaseProcess $keytool @('-exportcert','-storetype','PKCS12','-keystore',$temporary,'-alias',$Alias,'-file',$temporaryCertificate,'-storepass:env','WEALTHOS_KEYTOOL_PASSWORD') -SecretEnvironment $secret -Operation 'Public certificate export'
    $certificate=[Security.Cryptography.X509Certificates.X509Certificate2]::new([IO.File]::ReadAllBytes($temporaryCertificate))
    try{
        $metadata=[ordered]@{alias=$Alias;format='PKCS12';algorithm='RSA';bits=4096;subject=$certificate.Subject;certificateSha256=$certificate.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256).ToLowerInvariant();expiresUtc=$certificate.NotAfter.ToUniversalTime().ToString('o');privateKeyExported=$false;passwordStored=$false}
    }finally{$certificate.Dispose()}
    [IO.File]::Move($temporary,$destination,$false)
    [IO.File]::Move($temporaryCertificate,$certificatePath,$false)
    $json=$metadata | ConvertTo-Json
    $stream=[IO.File]::Open($metadataPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($json+"`n");$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
    Write-Output "Encrypted signing keystore created: $destination"
    Write-Output "Retain an independently protected copy of this keystore and its passphrase before distributing an APK. Only public certificate metadata follows."
    Write-Output $json
}finally{
    if($characters){[Array]::Clear($characters,0,$characters.Length)}
    if($ownsPassword -and $Password){$Password.Dispose()}
    # Exact unique files created by this invocation; never delete a destination or directory.
    foreach($ownedFile in @($temporary,$temporaryCertificate)){if(Test-Path -LiteralPath $ownedFile){Remove-Item -LiteralPath $ownedFile}}
}
