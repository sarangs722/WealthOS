package com.wealthos.app

import com.wealthos.database.BackupInfo
import com.wealthos.database.SafetyBackup

data class BackupState(val exportId:String?=null,val preview:BackupInfo?=null,val currentRecords:Int=0,val safety:List<SafetyBackup> = emptyList(),val message:String?=null,val source:android.net.Uri?=null,val openScreen:Boolean=false)
