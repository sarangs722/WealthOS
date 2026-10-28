package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable internal fun BackupScreen(state:BankUiState,model:BankViewModel,createFile:(String)->Unit,chooseFile:()->Unit){
    var password by remember {mutableStateOf("")};var confirmation by remember {mutableStateOf("")}
    var restorePassword by remember {mutableStateOf("")}
    val source=if(state.demo)null else state.backup.source
    var localError by remember {mutableStateOf<String?>(null)}
    var replaceConfirmed by remember {mutableStateOf(false)}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner){
        model.backupsForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer=LifecycleEventObserver {_,event->when(event){Lifecycle.Event.ON_START->model.backupsForeground(true);Lifecycle.Event.ON_STOP->{password="";confirmation="";restorePassword="";model.backupsForeground(false)};else->Unit}}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer);password="";confirmation="";restorePassword="";model.backupsForeground(false);model.leaveBackups()}
    }
    LaunchedEffect(source){restorePassword="";localError=null}
    LaunchedEffect(state.backup.exportId,state.demo){val id=state.backup.exportId;if(!state.demo && id!=null && model.claimExportPicker(id))createFile("WealthOS-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss"))}.wosbackup")}
    val enabled=!state.loading && !state.saving && !state.demo && state.refreshing==null && state.backup.preview==null && state.backup.exportId==null
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("backup-screen").padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Text("Backup & restore",style=MaterialTheme.typography.headlineMedium)
        Text("Your vault, beyond this device",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
        Text("Encrypted backups work after reinstalling or moving devices. Keep the file and its original password separately; a forgotten backup password cannot be recovered.")
        if(state.demo)Text("Backups are unavailable in fictional demo mode.",color=MaterialTheme.colorScheme.primary)
        if(state.refreshing!=null)Text("Wait for public refresh to finish before backing up or restoring.")
        if(state.saving){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Securing or validating your backup…")}
        state.formError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        localError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        state.backup.message?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
        SectionCard("Create portable backup"){
            Text("Includes your financial records, balance history, goals, scenarios and saved public references. Choose where to save the encrypted file.")
            BackupPassword("Backup password",password,enabled){password=it}
            BackupPassword("Confirm backup password",confirmation,enabled){confirmation=it}
            Button({
                if(password!=confirmation){localError="The backup passwords do not match."}
                else {localError=null;model.prepareExport(password.toCharArray());password="";confirmation=""}
            },enabled=enabled,modifier=Modifier.fillMaxWidth()){Text("Create encrypted backup")}
        }
        SectionCard("Restore a backup"){
            Text("Restore replaces the current vault. WealthOS checks the file and its records first; review the preview before confirming replacement.")
            OutlinedButton(chooseFile,enabled=enabled,modifier=Modifier.fillMaxWidth()){Text("Choose backup file")}
            Text(if(source==null)"No backup selected" else "Backup file selected; enter its original password.")
            BackupPassword("Restore password",restorePassword,enabled && source!=null){restorePassword=it}
            Button({val uri=source;if(uri!=null){localError=null;model.prepareRestore(uri,restorePassword.toCharArray());restorePassword="";replaceConfirmed=false}},enabled=enabled && source!=null,modifier=Modifier.fillMaxWidth()){Text("Validate and preview backup")}
            Text("An encrypted safety copy of this vault is required before replacement. It uses the password of the backup you restore. Export it to keep it after uninstalling; failed replacement preserves current records.")
        }
        if(!state.demo && state.backup.safety.isNotEmpty())SectionCard("Pre-restore safety copies"){
            Text("${state.backup.safety.size} encrypted copies retained on this device. Each uses the password of the backup restored during that operation. No safety copies are automatically deleted.")
            state.backup.safety.forEachIndexed {n,s->OutlinedButton({model.prepareSafetyExport(s.id)},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text("Export safety copy ${n+1} · ${s.bytes/1024+1} KiB")}}
        }
        Text("Device lock and amount-hiding settings stay local. Restoring turns public networking off. Format 1 supports up to 8 MiB of encoded financial data and 50,000 saved records, including history and audit. Incompatible, oversized or damaged files leave current records intact.",style=MaterialTheme.typography.bodySmall)
    }
    state.backup.preview?.let {info->AlertDialog(onDismissRequest={if(!state.saving)model.discardRestore()},title={Text("Replace this financial vault?")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Backup created ${recordedLabel(info.createdAt)}\nSchema ${info.schema} · ${info.records} financial/audit rows\nCurrent vault: ${state.backup.currentRecords} rows")
            info.tables.filterValues {it>0}.forEach {(name,count)->Text("${name.replace('_',' ')}: $count",style=MaterialTheme.typography.bodySmall)}
            Text("All current financial records will be replaced. An encrypted safety copy will be kept locally under this backup's password. Networking will be off; your device security settings stay local.")
            Row {Checkbox(replaceConfirmed,{replaceConfirmed=it},enabled=!state.saving,modifier=Modifier.semanticsLabel("Confirm replacement of current records"));Text("I understand that this replaces the current vault.",Modifier.weight(1f))}
        }
    },confirmButton={TextButton({model.confirmRestore()},enabled=replaceConfirmed && !state.saving){Text("Replace from validated backup")}},dismissButton={TextButton({model.discardRestore()},enabled=!state.saving){Text("Cancel restore")}})}
}
@Composable private fun BackupPassword(label:String,value:String,enabled:Boolean,change:(String)->Unit){
    OutlinedTextField(value,{if(it.length<=128)change(it)},enabled=enabled,label={Text(label)},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth(),supportingText={Text("12–128 characters; a long unique passphrase is recommended.")})
}
