package tr.com.gundemradari.scan

import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.*
import tr.com.gundemradari.religion.ReligionTracker
import tr.com.gundemradari.web.TurkishCoverageResolver
import java.util.UUID

data class ScanOutcome(val newItems:Int,val failed:List<String>)

class ScanCoordinator(private val db:AppDatabase){
    private val dao=db.dao()
    private val client=FeedClient()
    private val translator=NewsTranslator()
    private val coverageResolver=TurkishCoverageResolver()

    suspend fun scan(onProgress:(String)->Unit):ScanOutcome=coroutineScope{
        val started=System.currentTimeMillis()
        val sources=dao.enabledSources()
        onProgress("${sources.size} kaynak taranıyor")
        val outcomes=sources.map{s->async{runCatching{client.fetch(s)}}}.awaitAll()
        val failures=mutableListOf<String>()
        var count=0

        outcomes.forEachIndexed{index,result->
            val source=sources[index]
            result.onSuccess{items->
                for(item in items){
                    if(dao.rawExists(item.url))continue
                    val translated=translator.translateIfNeeded(item)
                    val initialImportance=scores(item.originalTitle,item.source).first
                    val localized=if(
                        source.groupName=="world" && initialImportance>=50
                    ){
                        runCatching{coverageResolver.improve(item,translated)}.getOrElse{translated}
                    }else translated
                    if(persist(localized))count++
                }
            }.onFailure{failures+=source.name}
        }

        dao.scan(ScanHistoryEntity(
            startedAt=started,
            finishedAt=System.currentTimeMillis(),
            newItems=count,
            failedSources=failures.size
        ))
        onProgress("$count yeni kayıt, ${failures.size} kaynak hatası")
        ScanOutcome(count,failures)
    }

    private suspend fun persist(item:FetchedItem):Boolean=db.withTransaction{
        val now=System.currentTimeMillis()
        val raw=RawItemEntity(
            url=item.url,
            sourceId=item.source.id,
            title=item.title,
            summary=item.summary,
            publishedAt=item.publishedAt,
            firstSeenAt=now,
            exactHash=exactHash(item.title,item.url),
            originalTitle=item.originalTitle,
            originalSummary=item.originalSummary
        )
        if(dao.addRaw(raw)==-1L)return@withTransaction false

        val religionPriority=ReligionTracker.priority(item.source,item.title,item.summary)
        val candidate=dao.recentEvents(now-14L*24*3600*1000).firstOrNull{isSameEvent(item,it)}

        if(candidate==null){
            val (importance,noise)=scores(item.title,item.source)
            val id=UUID.randomUUID().toString()
            val scope=when{
                item.source.groupName=="religion_search"||item.source.groupName=="religion_direct"->"religion"
                item.source.groupName=="turkey"||item.source.groupName=="social"||
                    item.title.contains("türkiye",true)||item.title.contains("turkey",true)->"turkey"
                else->"world"
            }
            val verify=when(item.source.groupName){
                "official","religion_direct"->4
                "social"->1
                else->2
            }
            dao.putEvent(EventEntity(
                id=id,
                title=item.title,
                summary=item.summary,
                scope=scope,
                importance=importance,
                noise=noise,
                verification=verify,
                velocity=0.0,
                sourceCount=1,
                firstSeenAt=now,
                updatedAt=now,
                changeNote="İlk kayıt",
                publishedAt=item.publishedAt,
                religionPriority=religionPriority
            ))
            dao.link(EventItemEntity(id,item.url))
            dao.addVersion(EventVersionEntity(
                eventId=id,version=1,title=item.title,summary=item.summary,
                changeNote="İlk kayıt",createdAt=now
            ))
        }else{
            val next=candidate.sourceCount+1
            val (i,n)=scores(item.title,item.source,next)
            val verification=maxOf(
                candidate.verification,
                when(item.source.groupName){
                    "official","religion_direct"->4
                    "social"->candidate.verification
                    else->if(next>=2)3 else 2
                }
            )
            val eventPublished=listOfNotNull(candidate.publishedAt,item.publishedAt).maxOrNull()
            val preferIncoming=
                candidate.title.any{it in 'a'..'z'} &&
                item.title.any{it in "çğıöşüÇĞİÖŞÜ"}

            dao.putEvent(candidate.copy(
                title=if(preferIncoming)item.title else candidate.title,
                summary=if(preferIncoming&&item.summary.isNotBlank())item.summary else candidate.summary,
                importance=maxOf(candidate.importance,i),
                noise=minOf(candidate.noise,n),
                verification=verification,
                sourceCount=next,
                updatedAt=now,
                changeNote="Yeni kaynak eklendi",
                publishedAt=eventPublished,
                religionPriority=maxOf(candidate.religionPriority,religionPriority)
            ))
            dao.link(EventItemEntity(candidate.id,item.url))
            dao.addVersion(EventVersionEntity(
                eventId=candidate.id,version=next,title=item.title,summary=item.summary,
                changeNote="Yeni kaynak eklendi",createdAt=now
            ))
        }
        true
    }
}
