package tr.com.gundemradari.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tr.com.gundemradari.data.*
import tr.com.gundemradari.research.EventResearchReport
import tr.com.gundemradari.research.EventResearchService
import tr.com.gundemradari.scan.ScanCoordinator
import tr.com.gundemradari.scan.likelySameSearchEvent
import tr.com.gundemradari.web.NewsWebSearch
import tr.com.gundemradari.web.webSourceQuality
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class FeedTab(val label:String,val icon:ImageVector){
    SEARCH("Arananlar",Icons.Default.Search),
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
    private val webSearch=NewsWebSearch()

    val tab=MutableStateFlow(FeedTab.TURKEY)
    val scanning=MutableStateFlow(false)
    val scanMessage=MutableStateFlow("Henüz taranmadı")
    val researchLoading=MutableStateFlow(false)
    val researchReport=MutableStateFlow<EventResearchReport?>(null)
    val researchError=MutableStateFlow<String?>(null)
    val activeSearchQuery=MutableStateFlow("")
    private val searchResults=MutableStateFlow<List<EventEntity>>(emptyList())
    private val hiddenSearchIds=MutableStateFlow<Set<String>>(emptySet())
    private var searchJob:Job?=null
    private val rankingClock=MutableStateFlow(System.currentTimeMillis())

    val sources=dao.sources()
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    val lastScan=dao.lastScan()
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)

    val events=combine(tab,rankingClock,hiddenSearchIds){t,now,hidden->
        Triple(t,now,hidden)
    }
        .flatMapLatest{(t,now,hidden)->
            val feed=when(t){
                FeedTab.SEARCH->searchResults
                FeedTab.TURKEY->dao.turkeyFeed(now)
                FeedTab.WORLD->dao.worldFeed(now)
                FeedTab.RELIGION->dao.religionFeed(now)
            }
            feed.map{list->
                if(t==FeedTab.SEARCH)list else list.filterNot{it.id in hidden}
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

    fun scan()=viewModelScope.launch{
        resetSearch()
        runScan()
    }

    fun startSearch(query:String){
        val clean=query.trim()
        if(clean.isBlank())return

        searchJob?.cancel()
        activeSearchQuery.value=clean
        searchResults.value=emptyList()
        hiddenSearchIds.value=emptySet()
        tab.value=FeedTab.SEARCH

        searchJob=viewModelScope.launch{
            val terms=normalizeSearch(clean)
                .split(" ")
                .filter{it.length>=2}
                .distinct()

            if(terms.isEmpty())return@launch

            suspend fun collectScope(scope:String){
                val now=System.currentTimeMillis()
                val rows=dao.searchEvents(
                    scope=scope,
                    now=now
                )

                val scored=rows.mapNotNull{row->
                    val score=searchMatchScore(
                        terms=terms,
                        title=row.event.title,
                        summary=row.event.summary,
                        fullText=row.searchText
                    )
                    if(score<=0.0)null else row.event to score
                }

                val minMatched=if(terms.size<=2)terms.size else terms.size-1
                val hits=scored
                    .filter{(event,_)->
                        matchedSearchTerms(
                            terms,
                            event.title+" "+event.summary
                        )>=minMatched
                    }
                    .sortedByDescending{(event,score)->
                        score+searchRank(event,now)
                    }
                    .map{it.first}

                if(hits.isNotEmpty()){
                    searchResults.value=mergeSearchEvents(
                        searchResults.value,
                        hits,
                        now
                    )
                    hiddenSearchIds.value=searchResults.value
                        .filterNot{it.id.startsWith("websearch:")}
                        .map{it.id}
                        .toSet()
                }
            }

            // Kullanıcıya en hızlı yerel sonuçları önce ver.
            collectScope("turkey")

            delay(300)
            collectScope("world")

            delay(400)
            collectScope("religion")

            // Yerel arşiv az sonuç verdiyse Google News ile sessizce genişlet.
            if(searchResults.value.size<12 && isActive){
                val now=System.currentTimeMillis()
                val external=runCatching{
                    webSearch.search(
                        query=clean,
                        limit=12,
                        expandDescriptions=false
                    )
                }.getOrElse{emptyList()}
                    .filter{row->
                        val hay=normalizeSearch(row.title+" "+row.snippet)
                        terms.all{hay.contains(it)}
                    }
                    .map{row->
                        EventEntity(
                            id="websearch:${row.url.hashCode()}",
                            title=row.title,
                            summary=row.snippet,
                            scope="search",
                            importance=externalSearchImportance(
                                row.title,
                                row.snippet,
                                row.source,
                                row.url,
                                row.publishedAt,
                                now
                            ),
                            noise=0.0,
                            verification=0,
                            velocity=0.0,
                            sourceCount=1,
                            firstSeenAt=row.publishedAt?:now,
                            updatedAt=row.publishedAt?:now,
                            changeNote="",
                            publishedAt=row.publishedAt,
                            religionPriority=0,
                            topic="search",
                            bestContentQuality=webSourceQuality(
                                row.source,
                                row.url
                            )*100.0
                        )
                    }

                searchResults.value=mergeSearchEvents(
                    searchResults.value,
                    external,
                    now
                )
            }
        }
    }

    private fun mergeSearchEvents(
        current:List<EventEntity>,
        incoming:List<EventEntity>,
        now:Long
    ):List<EventEntity>{
        val merged=current.toMutableList()

        incoming.forEach{candidate->
            val matchIndex=merged.indexOfFirst{existing->
                likelySameSearchEvent(existing,candidate)
            }

            if(matchIndex<0){
                merged+=candidate
            }else{
                val existing=merged[matchIndex]
                val existingIsLocal=!existing.id.startsWith("websearch:")
                val candidateIsLocal=!candidate.id.startsWith("websearch:")

                merged[matchIndex]=when{
                    existingIsLocal && !candidateIsLocal->
                        if(existing.summary.isBlank()&&candidate.summary.isNotBlank())
                            existing.copy(summary=candidate.summary)
                        else existing

                    candidateIsLocal && !existingIsLocal->
                        if(candidate.summary.isBlank()&&existing.summary.isNotBlank())
                            candidate.copy(summary=existing.summary)
                        else candidate

                    searchRank(candidate,now)>searchRank(existing,now)->
                        candidate

                    else->existing
                }
            }
        }

        return merged
            .distinctBy{it.id}
            .sortedByDescending{searchRank(it,now)}
    }

    private fun searchRank(event:EventEntity,now:Long):Double{
        val agePenalty=kotlin.math.min(
            30.0,
            kotlin.math.max(
                0.0,
                ((now-event.updatedAt)/3600000.0)*0.55
            )
        )
        val qualityBonus=((event.bestContentQuality-50.0)*0.08)
            .coerceIn(-4.0,4.0)
        return event.importance-agePenalty+qualityBonus
    }

    private fun externalSearchImportance(
        title:String,
        summary:String,
        sourceName:String,
        url:String,
        publishedAt:Long?,
        now:Long
    ):Double{
        val text=normalizeSearch("$title $summary")
        val quality=webSourceQuality(sourceName,url)
        var score=44.0 + (quality-0.50)*20.0

        val high=listOf(
            "deprem","yangın","savaş","saldırı","seçim","referandum",
            "can kaybı","öldü","afet","kriz","faiz","anayasa","tahliye"
        )
        score+=high.count{text.contains(it)}*5.0

        if(publishedAt!=null){
            val ageHours=((now-publishedAt).coerceAtLeast(0L))/3600000.0
            if(ageHours<=6)score+=7.0
            else if(ageHours<=24)score+=4.0
        }

        return score.coerceIn(32.0,86.0)
    }

    fun resetSearch(){
        searchJob?.cancel()
        searchJob=null
        activeSearchQuery.value=""
        searchResults.value=emptyList()
        hiddenSearchIds.value=emptySet()
        if(tab.value==FeedTab.SEARCH)tab.value=FeedTab.TURKEY
    }

    private fun normalizeSearch(text:String):String=
        text.lowercase(Locale("tr","TR"))
            .replace('ı','i')
            .replace('ş','s')
            .replace('ğ','g')
            .replace('ü','u')
            .replace('ö','o')
            .replace('ç','c')
            .replace(Regex("[^\\p{L}\\p{N}]+")," ")
            .replace(Regex("\\s+")," ")
            .trim()

    private fun matchedSearchTerms(
        terms:List<String>,
        text:String
    ):Int{
        val hay=normalizeSearch(text)
        return terms.count{term->
            hay.contains(term) ||
            hay.split(" ").any{token->
                token.length>=4 &&
                term.length>=4 &&
                (
                    token.startsWith(term) ||
                    term.startsWith(token)
                )
            }
        }
    }

    private fun searchMatchScore(
        terms:List<String>,
        title:String,
        summary:String,
        fullText:String
    ):Double{
        val titleN=normalizeSearch(title)
        val summaryN=normalizeSearch(summary)
        val fullN=normalizeSearch(fullText)

        var score=0.0
        var matched=0

        terms.forEach{term->
            val titleHit=titleN.contains(term)
            val summaryHit=summaryN.contains(term)
            val fullHit=fullN.contains(term)

            when{
                titleHit->{
                    score+=6.0
                    matched++
                }
                summaryHit->{
                    score+=3.0
                    matched++
                }
                fullHit->{
                    score+=1.5
                    matched++
                }
                else->{
                    val fuzzy=fullN.split(" ").any{token->
                        token.length>=4 &&
                        term.length>=4 &&
                        (
                            token.startsWith(term) ||
                            term.startsWith(token)
                        )
                    }
                    if(fuzzy){
                        score+=0.8
                        matched++
                    }
                }
            }
        }

        if(matched==terms.size)score+=4.0
        return score
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
    val activeSearchQuery by vm.activeSearchQuery.collectAsStateWithLifecycle()
    var searchText by rememberSaveable{mutableStateOf("")}

    LaunchedEffect(activeSearchQuery){
        if(activeSearchQuery.isBlank())searchText=""
    }

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

        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            ScrollableTabRow(
                selectedTabIndex=pagerState.currentPage,
                modifier=Modifier.weight(1f),
                edgePadding=4.dp,
                containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),
                divider={}
            ){
                FeedTab.entries.forEachIndexed{index,t->
                    Tab(
                        selected=pagerState.currentPage==index,
                        onClick={scope.launch{pagerState.animateScrollToPage(index)}},
                        modifier=Modifier.widthIn(min=108.dp),
                        selectedContentColor=tabAccent(t),
                        unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        text={
                            Row(
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(6.dp)
                            ){
                                Icon(
                                    t.icon,
                                    contentDescription=null,
                                    modifier=Modifier.size(17.dp)
                                )
                                Text(
                                    t.label,
                                    maxLines=1,
                                    softWrap=false
                                )
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

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ){
            HorizontalPager(
                state=pagerState,
                modifier=Modifier.fillMaxSize()
            ){
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

            if(scanning){
                CircularProgressIndicator(
                    modifier=Modifier
                        .align(Alignment.BottomStart)
                        .padding(start=12.dp,bottom=18.dp)
                        .size(18.dp),
                    strokeWidth=2.dp
                )
            }

            OutlinedTextField(
                value=searchText,
                onValueChange={searchText=it},
                modifier=Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end=14.dp,bottom=12.dp)
                    .widthIn(min=210.dp,max=290.dp),
                singleLine=true,
                placeholder={
                    Text(
                        if(activeSearchQuery.isBlank())"Haber ara"
                        else "Aranan: $activeSearchQuery"
                    )
                },
                shape=RoundedCornerShape(28.dp),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(
                    onSearch={vm.startSearch(searchText)}
                ),
                trailingIcon={
                    IconButton(
                        onClick={vm.startSearch(searchText)},
                        enabled=searchText.isNotBlank()
                    ){
                        Icon(Icons.Default.Search,contentDescription="Haber ara")
                    }
                },
                colors=OutlinedTextFieldDefaults.colors(
                    focusedContainerColor=MaterialTheme.colorScheme.surface.copy(alpha=.94f),
                    unfocusedContainerColor=MaterialTheme.colorScheme.surface.copy(alpha=.90f)
                )
            )
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
        FeedTab.SEARCH->MaterialTheme.colorScheme.primary
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
                when(tab){
                    FeedTab.SEARCH->"Sağ alttaki alandan haber ara."
                    FeedTab.RELIGION->"Takip edilen kişilerle ilgili henüz kayıt yok."
                    else->"Bu bölümde henüz önemli olay yok."
                }
            )
        }
        return
    }

    val listState=rememberLazyListState()

    Box(Modifier.fillMaxSize()){
        LazyColumn(
            state=listState,
            modifier=Modifier.fillMaxSize().padding(end=10.dp),
            contentPadding=PaddingValues(top=10.dp,bottom=82.dp),
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
        val progress=(listState.firstVisibleItemIndex.toFloat()/maxIndex)
            .coerceIn(0f,1f)
        val thumbFraction=(visibleCount.toFloat()/totalItems)
            .coerceIn(.08f,.45f)
        val thumbHeight=(maxHeight*thumbFraction).coerceAtLeast(34.dp)
        val maxOffset=(maxHeight-thumbHeight).coerceAtLeast(0.dp)
        val offset=maxOffset*progress

        val trackHeightPx=with(density){maxHeight.toPx()}
        val thumbHeightPx=with(density){thumbHeight.toPx()}
        val usableTrackPx=(trackHeightPx-thumbHeightPx).coerceAtLeast(1f)

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(18.dp)
                .pointerInput(totalItems,visibleCount,trackHeightPx){
                    var dragProgress=0f

                    detectVerticalDragGestures(
                        onDragStart={
                            dragProgress=(
                                listState.firstVisibleItemIndex.toFloat()/maxIndex
                            ).coerceIn(0f,1f)
                        },
                        onVerticalDrag={change,dragAmount->
                            change.consume()

                            dragProgress=(
                                dragProgress+dragAmount/usableTrackPx
                            ).coerceIn(0f,1f)

                            val targetIndex=(
                                dragProgress*maxIndex
                            ).roundToInt()

                            scope.launch{
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        ){
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
            )
        }
    }
}

private data class EventTone(
    val border:Color,
    val borderWidth:androidx.compose.ui.unit.Dp
)

@Composable private fun eventTone(importance:Double):EventTone=
    when{
        importance>=90->EventTone(
            border=Color(0xFF7A1616),
            borderWidth=3.dp
        )
        importance>=80->EventTone(
            border=Color(0xFFD75B5B),
            borderWidth=2.dp
        )
        importance>=68->EventTone(
            border=Color(0xFFD4A900),
            borderWidth=2.dp
        )
        else->EventTone(
            border=MaterialTheme.colorScheme.outlineVariant,
            borderWidth=1.dp
        )
    }

@Composable private fun EventCard(
    e:EventEntity,
    onResearch:()->Unit
){
    val tone=eventTone(e.importance)

    Card(
        modifier=Modifier
            .fillMaxWidth()
            .clickable(onClick=onResearch),
        colors=CardDefaults.cardColors(
            containerColor=Color.White,
            contentColor=Color.Black
        ),
        border=BorderStroke(
            tone.borderWidth,
            tone.border
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
                    color=Color.Black
                )

                if(e.summary.isNotBlank()){
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.summary,
                        style=MaterialTheme.typography.bodyMedium,
                        maxLines=4,
                        color=Color.Black.copy(alpha=.82f)
                    )
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
