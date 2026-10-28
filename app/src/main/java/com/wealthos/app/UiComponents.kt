package com.wealthos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wealthos.finance.AssetCategory

internal object WealthIcons {
    private fun icon(name:String,draw:androidx.compose.ui.graphics.vector.PathBuilder.()->Unit)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathBuilder=draw)
    }.build()
    val home=icon("Home"){moveTo(3f,10f);lineTo(12f,3f);lineTo(21f,10f);moveTo(5f,9f);lineTo(5f,21f);lineTo(10f,21f);lineTo(10f,14f);lineTo(14f,14f);lineTo(14f,21f);lineTo(19f,21f);lineTo(19f,9f)}
    val wallet=icon("Portfolio"){moveTo(20f,8f);lineTo(5f,8f);curveTo(2f,8f,2f,3f,5f,3f);lineTo(18f,3f);lineTo(18f,8f);moveTo(3f,6f);lineTo(3f,19f);quadTo(3f,21f,5f,21f);lineTo(21f,21f);lineTo(21f,8f);moveTo(21f,12f);lineTo(16f,12f);lineTo(16f,17f);lineTo(21f,17f)}
    val forecast=icon("Forecast"){moveTo(3f,4f);lineTo(3f,21f);lineTo(21f,21f);moveTo(6f,16f);lineTo(11f,11f);lineTo(15f,14f);lineTo(21f,6f);moveTo(16f,6f);lineTo(21f,6f);lineTo(21f,11f)}
    val calculator=icon("Tools"){moveTo(6f,3f);lineTo(18f,3f);lineTo(18f,21f);lineTo(6f,21f);close();moveTo(9f,7f);lineTo(15f,7f);moveTo(9f,11f);lineTo(10f,11f);moveTo(14f,11f);lineTo(15f,11f);moveTo(9f,15f);lineTo(10f,15f);moveTo(14f,15f);lineTo(15f,15f);moveTo(9f,18f);lineTo(10f,18f);moveTo(14f,18f);lineTo(15f,18f)}
    val shield=icon("Protect"){moveTo(12f,3f);lineTo(20f,6f);lineTo(20f,12f);quadTo(20f,18f,12f,22f);quadTo(4f,18f,4f,12f);lineTo(4f,6f);close();moveTo(9f,12f);lineTo(11f,14f);lineTo(15f,10f)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WealthFrame(tab:Int,title:String,main:Boolean,demo:Boolean,saving:Boolean,
    onTab:(Int)->Unit,onBack:()->Unit,onSettings:()->Unit,onExitDemo:()->Unit,onAdd:()->Unit,
    addVisible:Boolean=true,
    content:@Composable ()->Unit) {
    Scaffold(
        topBar={TopAppBar(title={Text(title,style=MaterialTheme.typography.titleLarge)},
            navigationIcon={if(!main)IconButton(onBack,enabled=!saving){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
            actions={if(main)IconButton(onSettings,enabled=!saving){Icon(Icons.Default.Settings,"Settings")}},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))},
        bottomBar={if(main)NavigationBar(containerColor=MaterialTheme.colorScheme.surfaceContainerLow){
            val labels=listOf("Home","Portfolio","Forecast","Tools","Protect")
            val icons=listOf(WealthIcons.home,WealthIcons.wallet,WealthIcons.forecast,WealthIcons.calculator,WealthIcons.shield)
            labels.forEachIndexed {index,label->NavigationBarItem(selected=tab==index,onClick={onTab(index)},enabled=!saving,modifier=Modifier.testTag("nav-$index"),
                icon={Icon(icons[index],null)},label={Text(label,style=MaterialTheme.typography.labelMedium)})}
        }},
        floatingActionButton={if(main && tab in 0..1 && !demo && addVisible)ExtendedFloatingActionButton(
            onClick={if(!saving)onAdd()},icon={Icon(Icons.Default.Add,null)},text={Text("Add")},modifier=Modifier.semanticsLabel("Add record"))}
    ){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter){
            Column(Modifier.widthIn(max=840.dp).fillMaxSize()){
                if(demo)Surface(color=MaterialTheme.colorScheme.secondaryContainer){
                    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Text("Demo · Fictional records",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                        TextButton(onExitDemo){Text("Exit demo")}
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()){content()}
            }
        }
    }
}

/** Stack summary cards before large text or long exact amounts become cramped. */
@Composable internal fun AdaptivePair(stack:Boolean=false,tag:String="summary-pair",first:@Composable (Modifier)->Unit,second:@Composable (Modifier)->Unit){
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()){
        if(stack || density.fontScale>1.2f || maxWidth<340.dp)Column(Modifier.fillMaxWidth().testTag("$tag-stacked"),verticalArrangement=Arrangement.spacedBy(12.dp)){
            first(Modifier.fillMaxWidth());second(Modifier.fillMaxWidth())
        }else Row(Modifier.fillMaxWidth().testTag("$tag-inline"),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            first(Modifier.weight(1f));second(Modifier.weight(1f))
        }
    }
}

/** Exact formatted text: fit ordinary amounts; explicitly scroll exceptionally long ones. */
@Composable internal fun AmountDisplay(value:String,modifier:Modifier=Modifier,style:TextStyle=MaterialTheme.typography.titleLarge,color:Color=MaterialTheme.colorScheme.onSurface){
    val density=LocalDensity.current;val measurer=rememberTextMeasurer()
    val minimum=minOf(16f,style.fontSize.value).sp
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val minimumWidth=measurer.measure(value,style=style.copy(fontSize=minimum),softWrap=false).size.width
        if(minimumWidth>with(density){maxWidth.toPx()})Column{
            Text(value,modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),style=style.copy(fontSize=minimum),color=color,softWrap=false)
            Text("Swipe for full amount",style=MaterialTheme.typography.labelMedium,color=color)
        }else BasicText(value,modifier.fillMaxWidth(),style=style.copy(color=color),maxLines=1,softWrap=false,
            autoSize=TextAutoSize.StepBased(minFontSize=minimum,maxFontSize=style.fontSize,stepSize=1.sp))
    }
}

@Composable internal fun SectionHeading(title:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        if(action!=null)TextButton(onAction){Text(action)}
    }
}
@Composable internal fun StatusPill(text:String,warning:Boolean=false){
    Surface(color=if(warning)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.small){
        Text(text,Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,
            color=if(warning)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
@Composable internal fun DisclosureHeader(title:String,expanded:Boolean,onToggle:()->Unit,subtitle:String?=null){
    Surface(onClick=onToggle,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(if(expanded)Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(expanded)"Collapse $title" else "Expand $title")
        }
    }
}
@Composable internal fun DetailsDisclosure(title:String,initiallyExpanded:Boolean=false,content:@Composable ColumnScope.()->Unit){
    var expanded by rememberSaveable(title){mutableStateOf(initiallyExpanded)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        DisclosureHeader(title,expanded,{expanded=!expanded})
        if(expanded)Column(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable internal fun InfoHelp(title:String,body:String){
    var open by remember {mutableStateOf(false)}
    IconButton(onClick={open=true}){Icon(Icons.Default.Info,"Help for $title",tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(20.dp))}
    if(open)AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Text(body)},confirmButton={TextButton({open=false}){Text("Got it")}})
}
@Composable internal fun NavigationRow(title:String,subtitle:String?=null,icon:ImageVector?=null,onClick:()->Unit){
    Surface(onClick=onClick,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            if(icon!=null)Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AddRecordSheet(onDismiss:()->Unit,onBank:()->Unit,onAsset:(AssetCategory)->Unit,onExisting:()->Unit){
    var group by rememberSaveable {mutableStateOf("main")}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                if(group!="main")IconButton({group="main"}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to record types")}
                Text(when(group){"invest"->"Add an investment";"deposit"->"Add a deposit";"retire"->"Add retirement savings";else->"Add to portfolio"},style=MaterialTheme.typography.headlineSmall)
            }
            if(group=="main"){
                NavigationRow("Bank account","Savings, current or brokerage cash",WealthIcons.wallet,onBank)
                NavigationRow("Investment","Mutual funds, shares, ETFs, gold",WealthIcons.forecast,{group="invest"})
                NavigationRow("Fixed or recurring deposit","Track a deposit contract",Icons.Default.DateRange,{group="deposit"})
                NavigationRow("Retirement savings","EPF, PPF or NPS",WealthIcons.shield,{group="retire"})
                NavigationRow("Property",null,Icons.Default.Home,{onAsset(AssetCategory.PROPERTY)})
                NavigationRow("Loan",null,WealthIcons.wallet,{onAsset(AssetCategory.LOAN)})
                TextButton(onExisting,modifier=Modifier.fillMaxWidth()){Text("Update an existing record")}
            }else{
                val types=when(group){"invest"->listOf(AssetCategory.MUTUAL_FUND,AssetCategory.STOCK,AssetCategory.ETF,AssetCategory.GOLD,AssetCategory.COMMODITY);"deposit"->listOf(AssetCategory.FD,AssetCategory.RD);else->listOf(AssetCategory.EPF,AssetCategory.PPF,AssetCategory.NPS)}
                types.forEach {category->NavigationRow(category.label,onClick={onAsset(category)})}
            }
        }
    }
}
