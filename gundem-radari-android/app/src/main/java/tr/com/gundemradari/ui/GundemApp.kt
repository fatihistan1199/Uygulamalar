package tr.com.gundemradari.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
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
import kotlin.math.roundToInt

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
    private val rankingClock=MutableStateFlow(System.currentTimeMillis())

    val sources=dao.sources()
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    val lastScan=dao.lastScan()
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)

    val events=combine(tab,rankingClock){t,now->t to now}
        .flatMapLatest{(t,now)->
            when(t){
                FeedTab.TURKEY->dao.turkeyFeed(now)
                FeedTab.WORLD->dao.worldFeed(now)
                FeedTab.RELIGION->dao.religionFeed(now)
            }
        }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

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
            scanner.scan(onProgress={scanMessage.value=it})
            rankingClock.value=System.currentTimeMillis()
        }finally{
            scanning.value=false
        }
    }

    fun scan()=viewModelScope.launch{runScan()}
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

@Composable private fun HomeScreen(
    vm:GundemViewModel,
    onSettings:()->Unit,
    context:Context
){
    val tab by vm.tab.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val message by vm.scanMessage.collectAsStateWithLifecycle()
    val last by vm.lastScan.collectAsStateWithLifecycle()
    val research by vm.researchReport.collectAsStateWithLifecycle()
    val researchLoading by vm.researchLoading.collectAsStateWithLifecycle()
    val researchError by vm.researchError.collectAsStateWithLifecycle()

    val pagerState=rememberPagerState(initialPage=FeedTab.entries.indexOf(tab)){FeedTab.entries.size}
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
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically
        ){
            Column(Modifier.weight(1f)){
                Text("Gündem Radarı",style=MaterialTheme.typography.headlineSmall)
                Text(
                    last?.let{
                        "Son tarama: ${
                            DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,DateFormat.SHORT
                            ).format(Date(it))
                        }"
                    }?:message,
                    style=MaterialTheme.typography.bodySmall
                )
            }

            FilledTonalButton(onClick=vm::scan,enabled=!scanning){
                Icon(Icons.Default.Refresh,contentDescription=null,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if(scanning)"Taranıyor…" else "Şimdi tara")
            }
        }

        if(scanning){
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp))
        }

        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            TabRow(
                selectedTabIndex=pagerState.currentPage,
                modifier=Modifier.weight(1f),
                containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),
                divider={}
            ){
                FeedTab.entries.forEachIndexed{index,t->
                    Tab(
                        selected=pagerState.currentPage==index,
                        onClick={scope.launch{pagerState.animateScrollToPage(index)}},
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

            IconButton(onClick=onSettings,modifier=Modifier.padding(start=4.dp)){
                Icon(
                    Icons.Default.Settings,
                    contentDescription="Ayarlar",
                    tint=MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalPager(state=pagerState,modifier=Modifier.fillMaxSize()){
            val pageTab=FeedTab.entries[it]
            if(pageTab!=tab){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
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
            confirmButton={TextButton(onClick=vm::closeResearch){Text("İptal")}},
            text={
                Row(
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)
                ){
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Ana haber ve ek kaynaklar inceleniyor.")
                }
            }
        )
    }else if(research!=null){
        ResearchDialog(research!!,vm::closeResearch,context)
    }else if(researchError!=null){
        AlertDialog(
            onDismissRequest=vm::closeResearch,
            title={Text("Araştırma")},
            confirmButton={TextButton(onClick=vm::closeResearch){Text("Kapat")}},
            text={Text(researchError!!)}
        )
    }
}

@Composable private fun tabAccent(tab:FeedTab):Color=
    when(tab){
        FeedTab.TURKEY->MaterialTheme.colorScheme.primary
        FeedTab.WORLD->MaterialTheme.colorScheme.secondary
        FeedTab.RELIGION->MaterialTheme.colorScheme.tertiary
    }

@Composable private fun EventList(
    tab:FeedTab,
    events:List<EventEntity>,
    onResearch:(EventEntity)->Unit
){
    if(events.isEmpty()){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
            Text(
                if(tab==FeedTab.RELIGION)
                    "Takip edilen konularla ilgili henüz kayıt yok."
                else
                    "Bu bölümde henüz önemli olay yok."
            )
        }
        return
    }

    val listState=rememberLazyListState()

    Box(Modifier.fillMaxSize()){
        LazyColumn(
            state=listState,
            modifier=Modifier.fillMaxSize().padding(end=10.dp),
            contentPadding=PaddingValues(vertical=10.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)
        ){
            items(events,key={it.id}){e->
                EventCard(e){onResearch(e)}
            }
        }

        FeedScrollbar(
            listState=listState,
            totalItems=events.size,
            modifier=Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(10.dp)
        )
    }
}

@Composable private fun FeedScrollbar(
    listState:LazyListState,
    totalItems:Int,
    modifier:Modifier=Modifier
){
    val layoutInfo by remember{
        derivedStateOf{listState.layoutInfo}
    }
    val visibleCount=layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    if(totalItems<=visibleCount)return

    val scope=rememberCoroutineScope()
    val density=LocalDensity.current

    BoxWithConstraints(modifier.padding(vertical=8.dp)){
        val maxIndex=(totalItems-visibleCount).coerceAtLeast(1)
        val progress=(listState.firstVisibleItemIndex.toFloat()/maxIndex).coerceIn(0f,1f)
        val thumbFraction=(visibleCount.toFloat()/totalItems).coerceIn(.08f,.45f)
        val thumbHeight=(maxHeight*thumbFraction).coerceAtLeast(34.dp)
        val maxOffset=(maxHeight-thumbHeight).coerceAtLeast(0.dp)
        val offset=maxOffset*progress
        val thumbHeightPx=with(density){thumbHeight.toPx()}

        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f),
                    CircleShape
                )
        )

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y=offset)
                .width(6.dp)
                .height(thumbHeight)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha=.78f),
                    CircleShape
                )
                .pointerInput(totalItems,visibleCount){
                    detectVerticalDragGestures{change,dragAmount->
                        change.consume()
                        val usablePx=(size.height-thumbHeightPx).coerceAtLeast(1f)
                        val current=listState.firstVisibleItemIndex.toFloat()/maxIndex
                        val next=(current+dragAmount/usablePx).coerceIn(0f,1f)
                        scope.launch{
                            listState.scrollToItem((next*maxIndex).roundToInt())
                        }
                    }
                }
        )
    }
}

private data class EventTone(
    val background:Color,
    val foreground:Color
)

@Composable private fun eventTone(importance:Double):EventTone=
    when{
        importance>=90->EventTone(
            background=Color(0xFF7A1616),
            foreground=Color.White
        )
        importance>=80->EventTone(
            background=Color(0xFFFFD7D7),
            foreground=Color(0xFF4D1111)
        )
        importance>=68->EventTone(
            background=Color(0xFFFFF0A8),
            foreground=Color(0xFF493B00)
        )
        else->EventTone(
            background=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.50f),
            foreground=MaterialTheme.colorScheme.onSurface
        )
    }

@Composable private fun EventCard(
    e:EventEntity,
    onResearch:()->Unit
){
    val tone=eventTone(e.importance)

    Card(
        colors=CardDefaults.cardColors(
            containerColor=tone.background,
            contentColor=tone.foreground
        ),
        elevation=CardDefaults.cardElevation(
            defaultElevation=if(e.importance>=80)3.dp else 1.dp
        )
    ){
        SelectionContainer{
            Column(Modifier.padding(14.dp)){
                Text(
                    e.title,
                    style=MaterialTheme.typography.titleMedium,
                    color=tone.foreground
                )

                if(e.summary.isNotBlank()){
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.summary,
                        style=MaterialTheme.typography.bodyMedium,
                        maxLines=4,
                        color=tone.foreground.copy(alpha=.88f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.End
                ){
                    IconButton(
                        onClick=onResearch,
                        modifier=Modifier.size(32.dp)
                    ){
                        Icon(
                            Icons.Default.Search,
                            contentDescription="Araştır",
                            modifier=Modifier.size(19.dp),
                            tint=tone.foreground
                        )
                    }
                }
            }
        }
    }
}

private fun eventDate(millis:Long):String=
    SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR")).format(Date(millis))

private fun openInOpera(context:Context,url:String){
    val packages=listOf(
        "com.opera.browser","com.opera.browser.beta","com.opera.gx","com.opera.mini.native"
    )
    for(pkg in packages){
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url)).apply{
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if(intent.resolveActivity(context.packageManager)!=null){
            context.startActivity(intent)
            return
        }
    }
    context.startActivity(
        Intent(Intent.ACTION_VIEW,Uri.parse(url)).apply{
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
        title={
            Row(
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ){
                Icon(Icons.Default.Search,contentDescription=null)
                Text("Haber özeti")
            }
        },
        confirmButton={TextButton(onClick=onClose){Text("Kapat")}},
        text={
            SelectionContainer{
                Column(
                    Modifier
                        .heightIn(max=600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)
                ){
                    Text(report.event.title,style=MaterialTheme.typography.titleMedium)

                    SummarySection(
                        title="Ne oldu?",
                        lines=report.whatHappened
                    )

                    if(report.whyImportant.isNotEmpty()){
                        HorizontalDivider()
                        SummarySection(
                            title="Neden önemli?",
                            lines=report.whyImportant
                        )
                    }

                    if(report.latestSituation.isNotEmpty()){
                        HorizontalDivider()
                        SummarySection(
                            title="Son durum",
                            lines=report.latestSituation
                        )
                    }

                    if(report.displayArticles.isNotEmpty()){
                        HorizontalDivider()
                        Text("Kaynaklar",style=MaterialTheme.typography.labelLarge)
                        report.displayArticles.forEach{a->
                            Column(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                                Text(
                                    text=a.sourceName,
                                    color=MaterialTheme.colorScheme.primary,
                                    textDecoration=TextDecoration.Underline,
                                    style=MaterialTheme.typography.labelLarge,
                                    modifier=Modifier.clickable{openInOpera(context,a.url)}
                                )
                                if(a.title.isNotBlank()){
                                    Text(a.title,style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if(report.firstAt!=null){
                        HorizontalDivider()
                        Text("Uygulama zaman çizgisi",style=MaterialTheme.typography.labelLarge)
                        Text(
                            "İlk kayıt: ${eventDate(report.firstAt)}",
                            style=MaterialTheme.typography.bodySmall
                        )
                        if(report.latestAt!=null&&report.latestAt!=report.firstAt){
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

@Composable private fun SummarySection(
    title:String,
    lines:List<String>
){
    Text(title,style=MaterialTheme.typography.labelLarge)
    if(lines.isEmpty()){
        Text(
            "Kaynaklardan yeterli ayrıntı çıkarılamadı.",
            style=MaterialTheme.typography.bodySmall
        )
    }else{
        lines.forEach{
            Text("• $it",style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun SourcesScreen(
    vm:GundemViewModel,
    onBack:()->Unit
){
    val rows by vm.sources.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){
                Icon(Icons.Default.ArrowBack,contentDescription="Geri")
            }
            Icon(
                Icons.Default.Settings,
                contentDescription=null,
                tint=MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text("Kaynaklar",style=MaterialTheme.typography.headlineSmall)
        }

        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedButton(onClick={vm.action(1)}){Text("Tümünü seç")}
            OutlinedButton(onClick={vm.action(0)}){Text("Tümünü kaldır")}
        }

        LazyColumn{
            items(rows,key={it.id}){s->
                Row(
                    Modifier.fillMaxWidth().padding(vertical=9.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically
                ){
                    Column(Modifier.weight(1f)){
                        Text(s.name)
                        Text(
                            s.note,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked=s.enabled,
                        onCheckedChange={vm.setSource(s.id,it)},
                        enabled=!s.staged
                    )
                }
            }
        }
    }
}
