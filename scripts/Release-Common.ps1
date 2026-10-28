#requires -Version 7.4
Set-StrictMode -Version Latest

function Get-ReleaseTool([string]$Directory,[string]$RelativePath) {
    $path=[IO.Path]::GetFullPath((Join-Path $Directory $RelativePath))
    if(-not (Test-Path -LiteralPath $path -PathType Leaf)){throw "Required release tool is missing: $RelativePath"}
    return $path
}
function Get-SecretCharacters([Security.SecureString]$Secret) {
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToGlobalAllocUnicode($Secret)
    try {
        $characters=[char[]]::new($Secret.Length)
        for($index=0;$index -lt $characters.Length;$index++){$characters[$index]=[char]([int][Runtime.InteropServices.Marshal]::ReadInt16($pointer,$index*2) -band 0xffff)}
        if($characters -contains [char]10 -or $characters -contains [char]13){[Array]::Clear($characters,0,$characters.Length);throw 'Signing passwords cannot contain a newline.'}
        return ,$characters
    }finally{[Runtime.InteropServices.Marshal]::ZeroFreeGlobalAllocUnicode($pointer)}
}
function Get-ReleaseJava([string]$JavaDirectory) {
    $java=Get-ReleaseTool $JavaDirectory 'bin/java.exe'
    $version=Invoke-ReleaseProcess $java @('-version') -Operation 'Java version check'
    if($version -notmatch '(?:openjdk|java) version "21[.\"]'){throw 'Release signing tools require the verified Java 21 runtime.'}
    return $java
}
function Invoke-ReleaseProcess {
    param([string]$Executable,[string[]]$Arguments,[Security.SecureString[]]$Passwords=@(),[hashtable]$SecretEnvironment=@{},[string]$Operation='Release tool')
    $process=[Diagnostics.Process]::new()
    $started=$false
    $process.StartInfo.FileName=$Executable
    $process.StartInfo.UseShellExecute=$false
    $process.StartInfo.CreateNoWindow=$true
    $process.StartInfo.RedirectStandardOutput=$true
    $process.StartInfo.RedirectStandardError=$true
    $process.StartInfo.RedirectStandardInput=$true
    $process.StartInfo.StandardInputEncoding=[Text.UTF8Encoding]::new($false)
    foreach($argument in $Arguments){$process.StartInfo.ArgumentList.Add($argument)}
    try {
        foreach($entry in $SecretEnvironment.GetEnumerator()){
            $characters=Get-SecretCharacters $entry.Value
            try{$process.StartInfo.Environment[$entry.Key]=[string]::new($characters)}finally{[Array]::Clear($characters,0,$characters.Length)}
        }
        $null=$process.Start();$started=$true
        foreach($entry in $SecretEnvironment.GetEnumerator()){$null=$process.StartInfo.Environment.Remove($entry.Key)}
        $stdout=$process.StandardOutput.ReadToEndAsync()
        $stderr=$process.StandardError.ReadToEndAsync()
        foreach($password in $Passwords){
            $characters=Get-SecretCharacters $password
            try{$process.StandardInput.WriteLine($characters)}finally{[Array]::Clear($characters,0,$characters.Length)}
        }
        $process.StandardInput.Close()
        if(-not $process.WaitForExit(60000)){$process.Kill($true);$process.WaitForExit();throw "$Operation timed out."}
        $output=$stdout.GetAwaiter().GetResult()
        $errorText=$stderr.GetAwaiter().GetResult()
        # Do not echo provider diagnostics from operations that receive private passwords.
        if($process.ExitCode -ne 0){throw "$Operation failed (exit $($process.ExitCode)). Check the inputs, alias and credentials; no release was accepted."}
        return ($output+$errorText)
    }finally{
        foreach($entry in $SecretEnvironment.GetEnumerator()){$null=$process.StartInfo.Environment.Remove($entry.Key)}
        if($started -and -not $process.HasExited){$process.Kill($true);$process.WaitForExit()}
        $process.Dispose()
    }
}
function Assert-NewReleasePath([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    if(Test-Path -LiteralPath $resolved){throw 'The destination already exists. Release tools never overwrite a keystore or accepted artifact.'}
    if(-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($resolved)) -PathType Container)){throw 'Create the destination directory first.'}
    return $resolved
}
function Get-ApkXmlNodes([string]$SdkDirectory,[string]$Apk,[string]$Entry='AndroidManifest.xml') {
    $aapt=Get-ReleaseTool $SdkDirectory 'build-tools/36.1.0/aapt2.exe'
    $tree=Invoke-ReleaseProcess $aapt @('dump','xmltree','--file',$Entry,$Apk) -Operation 'APK XML inspection'
    $nodes=[Collections.Generic.List[object]]::new()
    $stack=[Collections.Generic.List[object]]::new()
    foreach($line in ($tree -split "`r?`n")){
        if($line -match '^(\s*)E: ([\w-]+) '){
            $indent=$Matches[1].Length
            while($stack.Count -gt 0 -and $stack[$stack.Count-1].indent -ge $indent){$stack.RemoveAt($stack.Count-1)}
            $parent=if($stack.Count -gt 0){$stack[$stack.Count-1].tag}else{''}
            $node=[PSCustomObject]@{tag=$Matches[2];parent=$parent;indent=$indent;attributes=@{}}
            $nodes.Add($node);$stack.Add($node)
        }elseif($line -match '^\s*A: (?:http://schemas\.android\.com/apk/res/android:)?([\w-]+)(?:\(0x[0-9a-f]+\))?=(.*)$'){
            if($stack.Count -eq 0){throw 'Unsupported manifest tree structure.'}
            $value=$Matches[2]
            if($value -match '^"([^"]*)"'){$value=$Matches[1]}
            # Re-match name because parsing the value updates PowerShell's $Matches.
            $null=$line -match '^\s*A: (?:http://schemas\.android\.com/apk/res/android:)?([\w-]+)'
            $stack[$stack.Count-1].attributes[$Matches[1]]=$value
        }
    }
    if($nodes.Count -eq 0){throw 'Empty/unrecognized APK manifest.'}
    return ,$nodes.ToArray()
}
