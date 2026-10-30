#requires -Version 7.4
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. "$PSScriptRoot/../Release-Components.ps1"
function Component([string]$Tag,[string]$Name,[string]$Exported,[hashtable]$Extra=@{}){
    [pscustomobject]@{tag=$Tag;attributes=(@{name=$Name;exported=$Exported}+$Extra)}
}
$identity='com.wealthos.app.release'
$base=@((Component activity 'com.wealthos.app.MainActivity' true),(Component provider 'androidx.startup.InitializationProvider' false @{authorities="$identity.androidx-startup"}),(Component service 'androidx.room.MultiInstanceInvalidationService' false),(Component receiver 'androidx.profileinstaller.ProfileInstallReceiver' true @{permission='android.permission.DUMP'}))
$reader=Component service 'com.wealthos.app.StatementReaderService' false @{isolatedProcess='true';stopWithTask='true';process=':statement_reader'}
Assert-ReleaseComponents $base $identity 14
Assert-ReleaseComponents ($base+@($reader)) $identity 15
$count=2
function Reject([object[]]$Nodes,[int]$Version=15){
    $rejected=$false
    try{Assert-ReleaseComponents $Nodes $identity $Version}catch{$rejected=$true}
    if(-not $rejected){throw 'Unsafe component fixture was accepted.'}
    $script:count++
}
foreach($pair in @(@('isolatedProcess','false'),@('isolatedProcess',$null),@('stopWithTask','false'),@('process',':other'),@('process',$null),@('externalService','true'),@('exported','true'))){
    $bad=Component service 'com.wealthos.app.StatementReaderService' false (@{isolatedProcess='true';stopWithTask='true';process=':statement_reader'})
    $bad.attributes[$pair[0]]=$pair[1];Reject ($base+@($bad))
}
Reject $base
Reject ($base+@($reader,$reader))
Reject ($base+@($reader)) 14
Reject ($base+@((Component service 'com.wealthos.app.Unreviewed' false)))
Reject ($base+@($reader,(Component instrumentation 'test.Probe' false)))
$badBase=@($base | ForEach-Object {[pscustomobject]@{tag=$_.tag;attributes=@{}+$_.attributes}});$badBase[2].attributes['exported']='true';Reject ($badBase+@($reader))
$badBase=@($base | ForEach-Object {[pscustomobject]@{tag=$_.tag;attributes=@{}+$_.attributes}});$badBase[3].attributes['permission']='';Reject ($badBase+@($reader))
$badBase=@($base | ForEach-Object {[pscustomobject]@{tag=$_.tag;attributes=@{}+$_.attributes}});$badBase[1].attributes['authorities']='another.app';Reject ($badBase+@($reader))
Write-Output "PASS: $count release component policy checks."
