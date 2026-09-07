package tr.com.gundemradari.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tr.com.gundemradari.data.*
import tr.com.gundemradari.research.EventResearchReport
import tr.com.gundemradari.research.EventResearchService
import tr.com.gundemradari.scan.ScanCoordinator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

enum class FeedTab(val label:String){
    MISS("Kaçırma"),
    TURKEY("Türkiye"),
    WORLD("Dünya"),
    MISSED("Kaçırmış Olabileceklerin")
}

class GundemViewModel(context:Context):ViewModel(){
    private val db=AppDatabase.get(context)
    private val dao=db.dao()
    private val repo=SourceRepository(context,dao)
    private val scanner=ScanCoordinator(db)
    private val researcher=EventResearchService(dao)
    val tab=MutableStateFlow(FeedTab.MISS)
    val scanning=MutableStateFlow(false)
    val scanMessage=MutableStateFlow("Henüz taranmadı")
    val sources=dao.sources().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val lastScan=dao.lastScan().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val researchReport=MutableStateFlow<EventResearchReport?>(null)
    val events=tab.flatMapLatest{
        when(it){
            FeedTab.MISS->dao.mainFeed()
            FeedTab.TURKEY->dao.turkeyFeed()
            FeedTab.WORLD->dao.worldFeed()
            FeedTab.MISSED->dao.missedFeed(System.currentTimeMillis()-7L*24*3600*1000)
        }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init{viewModelScope.launch{repo.seed()}}
    fun scan()=viewModelScope.launch{
        if(scanning.value)return@launch
        scanning.value=true
        scanMessage.value="Kaynaklar hazırlanıyor"
        val result=scanner.scan{scanMessage.value=it}
        if(result.failed.isNotEmpty())scanMessage.value+=" · Hata: ${result.failed.joinToString()}"
        scanning.value=false
    }
    fun setSource(id:String,enabled:Boolean)=viewModelScope.launch{dao.setSource(id,enabled)}
    fun action(mode:Int)=viewModelScope.launch{dao.setAll(mode)}
    fun research(event:EventEntity)=viewModelScope.launch{researchReport.value=researcher.build(event)}
    fun closeResearch(){researchReport.value=null}
}

@Composable fun GundemApp(context:Context){
    val vm:GundemViewModel=androidx.lifecycle.viewmodel.compose.viewModel(factory=object:ViewModelProvider.Factory{
        override fun <T:ViewModel> create(modelClass:Class<T>):T{
            @Suppress("UNCHECKED_CAST") return GundemViewModel(context) as T
        }
    })
    var settings by remember{mutableStateOf(false)}
    if(settings)SourcesScreen(vm,{settings=false}) else HomeScreen(vm,{settings=true})
}

@Composable private fun HomeScreen(vm:GundemViewModel,onSettings:()->Unit){
    val tab by vm.tab.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val message by vm.scanMessage.collectAsStateWithLifecycle()
    val last by vm.lastScan.collectAsStateWithLifecycle()
    val research by vm.researchReport.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column{
                Text("Gündem Radarı",style=MaterialTheme.typography.headlineSmall)
                Text(last?.let{"Son tarama: ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it))}"}?:message,style=MaterialTheme.typography.bodySmall)
            }
            Button(onClick=vm::scan,enabled=!scanning){Text(if(scanning)"Taranıyor…" else "Şimdi tara")}
        }
        if(scanning)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp))
        ScrollableTabRow(selectedTabIndex=FeedTab.entries.indexOf(tab),edgePadding=0.dp){
            FeedTab.entries.forEach{t->Tab(selected=tab==t,onClick={vm.tab.value=t},text={Text(t.label)})}
        }
        TextButton(onClick=onSettings){Text("⚙ Ayarlar › Kaynaklar")}
        if(events.isEmpty()){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Bu bölümde henüz önemli olay yok.\\nŞimdi tara düğmesine bas.")}
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(events,key={it.id}){e->EventCard(e,onResearch={vm.research(e)})}
        }
    }
    research?.let{ResearchDialog(it,vm::closeResearch)}
}

private fun eventDate(millis:Long):String=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR")).format(Date(millis))

@Composable private fun EventCard(e:EventEntity,onResearch:()->Unit){
    Card{
        Column(Modifier.padding(14.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("Önem ${e.importance.toInt()} · Doğrulama ${e.verification}/4",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text(eventDate(e.publishedAt?:e.updatedAt),style=MaterialTheme.typography.labelSmall)
            }
            Text(e.title,style=MaterialTheme.typography.titleMedium)
            if(e.summary.isNotBlank())Text(e.summary,style=MaterialTheme.typography.bodyMedium,maxLines=3)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("${e.sourceCount} kaynak · ${e.changeNote}",style=MaterialTheme.typography.labelSmall)
                IconButton(onClick=onResearch,modifier=Modifier.size(30.dp)){Text("🔍")}
            }
        }
    }
}

@Composable private fun ResearchDialog(report:EventResearchReport,onClose:()->Unit){
    val verification=when(report.event.verification){
        4->"Resmî kaynak doğruladı"
        3->"Birden fazla kaynak doğruluyor"
        2->"Tek güvenilir kaynak"
        1->"Erken/sosyal sinyal"
        else->"Henüz doğrulanmadı"
    }
    AlertDialog(
        onDismissRequest=onClose,
        title={Text("Araştırma")},
        confirmButton={TextButton(onClick=onClose){Text("Kapat")}},
        text={
            Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(report.event.title,style=MaterialTheme.typography.titleMedium)
                Text("${report.independentSourceCount} bağımsız kaynak · $verification",style=MaterialTheme.typography.bodyMedium)
                if(report.officialSourceCount>0)Text("${report.officialSourceCount} resmî kaynak bu olay kümesinde.")
                if(report.socialSourceCount>0)Text("${report.socialSourceCount} sosyal/erken sinyal kaynağı bulunuyor.")
                if(report.commonTerms.isNotEmpty()){
                    HorizontalDivider()
                    Text("Kaynakların ortak odağı",style=MaterialTheme.typography.labelLarge)
                    Text(report.commonTerms.joinToString(" · "))
                } else if(report.independentSourceCount<2){
                    Text("Tek kaynak bulunduğu için kaynak karşılaştırması sınırlı.",style=MaterialTheme.typography.bodySmall)
                }
                if(report.firstAt!=null){
                    HorizontalDivider()
                    Text("Zaman çizgisi",style=MaterialTheme.typography.labelLarge)
                    Text("İlk kayıt: ${eventDate(report.firstAt)}")
                    if(report.latestAt!=null && report.latestAt!=report.firstAt)Text("Son kayıt: ${eventDate(report.latestAt)}")
                }
                HorizontalDivider()
                Text("Kaynaklar",style=MaterialTheme.typography.labelLarge)
                report.sources.distinctBy{it.sourceName}.forEach{row->
                    Column(Modifier.padding(bottom=6.dp)){
                        Text(row.sourceName,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Text(row.title,style=MaterialTheme.typography.bodySmall)
                        Text(eventDate(row.publishedAt?:row.firstSeenAt),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    )
}

@Composable private fun SourcesScreen(vm:GundemViewModel,onBack:()->Unit){
    val rows by vm.sources.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)){
        TextButton(onClick=onBack){Text("‹ Gündem")}
        Text("Kaynaklar",style=MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedButton(onClick={vm.action(1)}){Text("Tümünü seç")}
            OutlinedButton(onClick={vm.action(0)}){Text("Tümünü kaldır")}
        }
        LazyColumn{
            items(rows,key={it.id}){s->
                Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(s.name)
                        Text(if(s.staged)"Beklemede — ${s.note}" else s.note,style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=s.enabled,onCheckedChange={vm.setSource(s.id,it)},enabled=!s.staged)
                }
            }
        }
    }
}
