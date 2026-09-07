package tr.com.gundemradari.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tr.com.gundemradari.data.*
import tr.com.gundemradari.religion.ReligionTracker
import tr.com.gundemradari.research.EventResearchReport
import tr.com.gundemradari.research.EventResearchService
import tr.com.gundemradari.scan.ScanCoordinator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

enum class FeedTab(val label:String,val icon:ImageVector){
    TURKEY("Türkiye",Icons.Default.Flag),
    WORLD("Dünya",Icons.Default.Public),
    RELIGION("Din",Icons.Default.MenuBook)
}

class GundemViewModel(context:Context):ViewModel(){
    private val db=AppDatabase.get(context)
    private val dao=db.dao()
    private val repo=SourceRepository(context,dao)
    private val scanner=ScanCoordinator(db)
    private val researcher=EventResearchService(dao)

    val tab=MutableStateFlow(FeedTab.TURKEY)
    val scanning=MutableStateFlow(false)
    val scanMessage=MutableStateFlow("Henüz taranmadı")
    val researchLoading=MutableStateFlow(false)
    val researchReport=MutableStateFlow<EventResearchReport?>(null)
    val researchError=MutableStateFlow<String?>(null)

    val sources=dao.sources().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val lastScan=dao.lastScan().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val events=tab.flatMapLatest{
        when(it){
            FeedTab.TURKEY->dao.turkeyFeed()
            FeedTab.WORLD->dao.worldFeed()
            FeedTab.RELIGION->dao.religionFeed()
        }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    init{
        viewModelScope.launch{
            repo.seed()
            runScan()
        }
    }

    private suspend fun runScan(){
        if(scanning.value)return
        scanning.value=true
        scanMessage.value="Kaynaklar hazırlanıyor"
        try{
            val result=scanner.scan{scanMessage.value=it}
            if(result.failed.isNotEmpty()){
                scanMessage.value+=" · Hata: ${result.failed.joinToString()}"
            }
        }finally{
            scanning.value=false
        }
    }

    fun scan()=viewModelScope.launch{
        runScan()
    }

    fun setSource(id:String,enabled:Boolean)=viewModelScope.launch{dao.setSource(id,enabled)}
    fun action(mode:Int)=viewModelScope.launch{dao.setAll(mode)}

    fun research(event:EventEntity)=viewModelScope.launch{
        researchLoading.value=true
        researchError.value=null
        researchReport.value=null
        runCatching{researcher.build(event)}
            .onSuccess{researchReport.value=it}
            .onFailure{researchError.value=it.message?:"Web araştırması yapılamadı."}
        researchLoading.value=false
    }

    fun closeResearch(){
        researchLoading.value=false
        researchReport.value=null
        researchError.value=null
    }
}

@Composable fun GundemApp(context:Context){
    val vm:GundemViewModel=androidx.lifecycle.viewmodel.compose.viewModel(
        factory=object:ViewModelProvider.Factory{
            override fun <T:ViewModel> create(modelClass:Class<T>):T{
                @Suppress("UNCHECKED_CAST")
                return GundemViewModel(context) as T
            }
        }
    )
    var settings by remember{mutableStateOf(false)}
    if(settings)SourcesScreen(vm,{settings=false})
    else HomeScreen(vm,{settings=true},context)
}

@Composable private fun HomeScreen(vm:GundemViewModel,onSettings:()->Unit,context:Context){
    val tab by vm.tab.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val message by vm.scanMessage.collectAsStateWithLifecycle()
    val last by vm.lastScan.collectAsStateWithLifecycle()
    val research by vm.researchReport.collectAsStateWithLifecycle()
    val researchLoading by vm.researchLoading.collectAsStateWithLifecycle()
    val researchError by vm.researchError.collectAsStateWithLifecycle()

    val pagerState=rememberPagerState(
        initialPage=FeedTab.entries.indexOf(tab)
    ){FeedTab.entries.size}
    val scope=rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage){
        vm.tab.value=FeedTab.entries[pagerState.currentPage]
    }

    LaunchedEffect(tab){
        val target=FeedTab.entries.indexOf(tab)
        if(target!=pagerState.currentPage)pagerState.scrollToPage(target)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween
        ){
            Column{
                Text("Gündem Radarı",style=MaterialTheme.typography.headlineSmall)
                Text(
                    last?.let{
                        "Son tarama: ${
                            DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT
                            ).format(Date(it))
                        }"
                    }?:message,
                    style=MaterialTheme.typography.bodySmall
                )
            }

            FilledTonalButton(onClick=vm::scan,enabled=!scanning){
                Icon(
                    Icons.Default.Refresh,
                    contentDescription=null,
                    modifier=Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if(scanning)"Taranıyor…" else "Şimdi tara")
            }
        }

        if(scanning){
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top=12.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically
        ){
            TabRow(
                selectedTabIndex=pagerState.currentPage,
                modifier=Modifier.weight(1f),
                containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),
                divider={}
            ){
                FeedTab.entries.forEachIndexed{index,t->
                    Tab(
                        selected=pagerState.currentPage==index,
                        onClick={
                            scope.launch{
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        selectedContentColor=tabAccent(t),
                        unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        text={
                            Row(
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(5.dp)
                            ){
                                Icon(t.icon,contentDescription=null,modifier=Modifier.size(17.dp))
                                Text(t.label)
                            }
                        }
                    )
                }
            }

            IconButton(
                onClick=onSettings,
                modifier=Modifier.padding(start=4.dp)
            ){
                Icon(
                    Icons.Default.Settings,
                    contentDescription="Ayarlar",
                    tint=MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalPager(
            state=pagerState,
            modifier=Modifier.fillMaxSize()
        ){
            val pageTab=FeedTab.entries[it]
            if(pageTab!=tab){
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment=Alignment.Center
                ){
                    CircularProgressIndicator()
                }
            }else{
                EventList(
                    tab=pageTab,
                    events=events,
                    onResearch={event->vm.research(event)}
                )
            }
        }
    }

    if(researchLoading){
        AlertDialog(
            onDismissRequest=vm::closeResearch,
            title={
                Row(
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ){
                    Icon(Icons.Default.Search,contentDescription=null)
                    Text("Geniş araştırma")
                }
            },
            confirmButton={
                TextButton(onClick=vm::closeResearch){
                    Text("İptal")
                }
            },
            text={
                Row(
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)
                ){
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(
                        "Ana haber sayfası açılıyor; ek haberler karşılaştırılıp anlaşılır bir özet hazırlanıyor."
                    )
                }
            }
        )
    }else if(research!=null){
        ResearchDialog(research!!,vm::closeResearch,context)
    }else if(researchError!=null){
        AlertDialog(
            onDismissRequest=vm::closeResearch,
            title={Text("Araştırma")},
            confirmButton={
                TextButton(onClick=vm::closeResearch){
                    Text("Kapat")
                }
            },
            text={Text(researchError!!)}
        )
    }
}

@Composable private fun EventList(
    tab:FeedTab,
    events:List<EventEntity>,
    onResearch:(EventEntity)->Unit
){
    if(events.isEmpty()){
        Box(
            Modifier.fillMaxSize(),
            contentAlignment=Alignment.Center
        ){
            Text(
                if(tab==FeedTab.RELIGION)
                    "Yalnız takip listesindeki isimler aranıyor.\nHenüz eşleşen kayıt yok."
                else
                    "Bu bölümde henüz önemli olay yok.\nŞimdi tara düğmesine bas."
            )
        }
    }else{
        LazyColumn(
            modifier=Modifier.fillMaxSize(),
            verticalArrangement=Arrangement.spacedBy(10.dp)
        ){
            items(events,key={it.id}){e->
                EventCard(tab,e){onResearch(e)}
            }
        }
    }
}

private fun eventDate(millis:Long):String=
    SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
        Locale("tr","TR")
    ).format(Date(millis))

@Composable private fun tabAccent(tab:FeedTab):Color=
    when(tab){
        FeedTab.TURKEY->MaterialTheme.colorScheme.primary
        FeedTab.WORLD->MaterialTheme.colorScheme.secondary
        FeedTab.RELIGION->MaterialTheme.colorScheme.tertiary
    }

@Composable private fun eventCardColor(tab:FeedTab,importance:Double):Color{
    if(importance>=85){
        return MaterialTheme.colorScheme.errorContainer.copy(alpha=.58f)
    }
    if(importance>=70){
        return MaterialTheme.colorScheme.tertiaryContainer.copy(alpha=.48f)
    }
    return when(tab){
        FeedTab.TURKEY->MaterialTheme.colorScheme.primaryContainer.copy(alpha=.44f)
        FeedTab.WORLD->MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.44f)
        FeedTab.RELIGION->MaterialTheme.colorScheme.tertiaryContainer.copy(alpha=.38f)
    }
}

@Composable private fun EventCard(
    tab:FeedTab,
    e:EventEntity,
    onResearch:()->Unit
){
    val accent=if(e.importance>=85)MaterialTheme.colorScheme.error else tabAccent(tab)

    Card(
        colors=CardDefaults.cardColors(
            containerColor=eventCardColor(tab,e.importance)
        ),
        elevation=CardDefaults.cardElevation(
            defaultElevation=if(e.importance>=80)3.dp else 1.dp
        )
    ){
        SelectionContainer{
            Column(Modifier.padding(14.dp)){
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically
                ){
                    Icon(
                        tab.icon,
                        contentDescription=null,
                        modifier=Modifier.size(17.dp),
                        tint=accent
                    )
                    if(e.importance>=80){
                        Spacer(Modifier.width(7.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(accent,CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))

                Text(
                    e.title,
                    style=MaterialTheme.typography.titleMedium
                )

                if(e.summary.isNotBlank()){
                    Spacer(Modifier.height(5.dp))
                    Text(
                        e.summary,
                        style=MaterialTheme.typography.bodyMedium,
                        maxLines=3,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(9.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically
                ){
                    Text(
                        "${e.sourceCount} kaynak · ${e.changeNote}",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalIconButton(
                        onClick=onResearch,
                        modifier=Modifier.size(32.dp)
                    ){
                        Icon(
                            Icons.Default.Search,
                            contentDescription="Araştır",
                            modifier=Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openInOpera(
    context:Context,
    url:String
){
    val packages=listOf(
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.opera.gx",
        "com.opera.mini.native"
    )

    for(pkg in packages){
        val intent=Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).apply{
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if(intent.resolveActivity(context.packageManager)!=null){
            context.startActivity(intent)
            return
        }
    }

    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).apply{
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

@Composable private fun ResearchDialog(
    report:EventResearchReport,
    onClose:()->Unit,
    context:Context
){
    AlertDialog(
        onDismissRequest=onClose,
        title={Text("Geniş araştırma")},
        confirmButton={
            TextButton(onClick=onClose){
                Text("Kapat")
            }
        },
        text={
            SelectionContainer{
                Column(
                    Modifier
                        .heightIn(max=600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)
                ){
                    Text(
                        report.event.title,
                        style=MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Ne oldu?",
                        style=MaterialTheme.typography.labelLarge
                    )

                    if(report.whatHappened.isEmpty()){
                        Text(
                            "Ana kaynaktan yeterli ayrıntı çıkarılamadı.",
                            style=MaterialTheme.typography.bodySmall
                        )
                    }

                    report.whatHappened.forEach{
                        Text(
                            "• $it",
                            style=MaterialTheme.typography.bodyMedium
                        )
                    }

                    if(report.details.isNotEmpty()){
                        HorizontalDivider()
                        Text(
                            "Önemli ayrıntılar",
                            style=MaterialTheme.typography.labelLarge
                        )

                        report.details.forEach{
                            Text(
                                "• $it",
                                style=MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if(report.background.isNotEmpty()){
                        HorizontalDivider()
                        Text(
                            "Bağlam",
                            style=MaterialTheme.typography.labelLarge
                        )

                        report.background.forEach{
                            Text(
                                "• $it",
                                style=MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if(report.displayArticles.isNotEmpty()){
                        HorizontalDivider()
                        Text(
                            "Kaynaklar",
                            style=MaterialTheme.typography.labelLarge
                        )

                        report.displayArticles.forEach{a->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical=5.dp)
                            ){
                                if(a.isPrimary){
                                    Text(
                                        "Ana kaynak",
                                        style=MaterialTheme.typography.labelSmall
                                    )
                                }

                                Text(
                                    text=a.sourceName,
                                    color=MaterialTheme.colorScheme.primary,
                                    textDecoration=TextDecoration.Underline,
                                    style=MaterialTheme.typography.labelLarge,
                                    modifier=Modifier.clickable{
                                        openInOpera(context,a.url)
                                    }
                                )

                                if(a.title.isNotBlank()){
                                    Text(
                                        a.title,
                                        style=MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    if(report.firstAt!=null){
                        HorizontalDivider()
                        Text(
                            "Uygulama zaman çizgisi",
                            style=MaterialTheme.typography.labelLarge
                        )

                        Text(
                            "İlk kayıt: ${eventDate(report.firstAt)}",
                            style=MaterialTheme.typography.bodySmall
                        )

                        if(
                            report.latestAt!=null &&
                            report.latestAt!=report.firstAt
                        ){
                            Text(
                                "Son kayıt: ${eventDate(report.latestAt)}",
                                style=MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable private fun SourcesScreen(
    vm:GundemViewModel,
    onBack:()->Unit
){
    val rows by vm.sources.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ){
        TextButton(onClick=onBack){
            Text("‹ Gündem")
        }

        Row(
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ){
            Icon(
                Icons.Default.Settings,
                contentDescription=null,
                tint=MaterialTheme.colorScheme.primary
            )
            Text(
                "Kaynaklar",
                style=MaterialTheme.typography.headlineSmall
            )
        }

        ElevatedCard(
            Modifier
                .fillMaxWidth()
                .padding(vertical=8.dp)
        ){
            Column(
                Modifier.padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(3.dp)
            ){
                Text(
                    "Din takip listesi",
                    style=MaterialTheme.typography.titleMedium
                )

                Text(
                    "Din sekmesinde yalnız aşağıdaki isim/başlıklar aranır; doğrudan açıklamalar önce gösterilir.",
                    style=MaterialTheme.typography.bodySmall
                )

                ReligionTracker.displayFollowList.forEach{
                    Text(
                        "• $it",
                        style=MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Row(
            horizontalArrangement=Arrangement.spacedBy(6.dp)
        ){
            OutlinedButton(
                onClick={vm.action(1)}
            ){
                Text("Tümünü seç")
            }

            OutlinedButton(
                onClick={vm.action(0)}
            ){
                Text("Tümünü kaldır")
            }
        }

        LazyColumn{
            items(rows,key={it.id}){s->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical=9.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically
                ){
                    Column(
                        Modifier.weight(1f)
                    ){
                        Text(s.name)
                        Text(
                            s.note,
                            style=MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked=s.enabled,
                        onCheckedChange={
                            vm.setSource(s.id,it)
                        },
                        enabled=!s.staged
                    )
                }
            }
        }
    }
}
