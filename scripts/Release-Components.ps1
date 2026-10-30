function Assert-ReleaseComponents {
    param([object[]]$Nodes,[string]$ApplicationId,[int]$VersionCode)
    if(@($Nodes | Where-Object tag -eq 'instrumentation').Count -ne 0){throw 'Test instrumentation must not be packaged in the financial application.'}
    $components=@($Nodes | Where-Object {$_.tag -in @('activity','activity-alias','provider','service','receiver')})
    $approved=@{'com.wealthos.app.MainActivity'=@('activity','true');'androidx.startup.InitializationProvider'=@('provider','false');'androidx.room.MultiInstanceInvalidationService'=@('service','false');'androidx.profileinstaller.ProfileInstallReceiver'=@('receiver','true')}
    if($VersionCode -ge 15){$approved['com.wealthos.app.StatementReaderService']=@('service','false')}
    if($components.Count -ne $approved.Count){throw 'The Android component inventory changed and requires review.'}
    $seen=@{}
    foreach($component in $components){
        $name=$component.attributes['name']
        if(-not $approved.ContainsKey($name) -or $seen.ContainsKey($name) -or $component.tag -ne $approved[$name][0] -or $component.attributes['exported'] -ne $approved[$name][1]){throw 'Unexpected/exported Android component.'}
        $seen[$name]=$true
        if($component.tag -eq 'receiver' -and $component.attributes['permission'] -ne 'android.permission.DUMP'){throw 'The AndroidX profile receiver lost its signature-protected permission.'}
        if($component.tag -eq 'provider' -and $component.attributes['authorities'] -ne "$ApplicationId.androidx-startup"){throw 'Provider authority does not belong to this release identity.'}
        if($name -eq 'com.wealthos.app.StatementReaderService'){
            if($component.attributes['isolatedProcess'] -ne 'true' -or $component.attributes['stopWithTask'] -ne 'true' -or
               $component.attributes['process'] -notin @(':statement_reader',"${ApplicationId}:statement_reader") -or $component.attributes['externalService'] -eq 'true'){
                throw 'Statement reader lost its private isolated-process boundary.'
            }
        }
    }
}
