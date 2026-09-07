package tr.com.gundemradari.scan

import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.*
import java.util.UUID

data class ScanOutcome(val newItems:Int,val failed:List<String>)

class ScanCoordinator(private val db:AppDatabase){
    private val dao=db.dao()
    private val client=FeedClient()
    private val translator=NewsTranslator()

    suspend fun scan(onProgress:(String)->Unit):ScanOutcome=coroutineScope{
        val started=System.currentTimeMillis()
        val sources=dao.enabledSources()
        onProgress("${sources.size} kaynak taranıyor")
        val outcomes=sources.map{s->async{runCatching{client.fetch(s)}}}.awaitAll()
        val failures=mutableListOf<String>()
        var count=0
        outcomes.forEachIndexed{index,result->
            result.onSuccess{items->
                for(item in items){
                    if(dao.rawExists(item.url)) continue
                    val localized=translator.translateIfNeeded(item)
                    if(persist(localized)) count++
                }
            }.onFailure{failures+=sources[index].name}
        }
        dao.scan(ScanHistoryEntity(startedAt=started,finishedAt=System.currentTimeMillis(),newItems=count,failedSources=failures.size))
        onProgress("$count yeni kayıt, ${failures.size} kaynak hatası")
        ScanOutcome(count,failures)
    }

    private suspend fun persist(item:FetchedItem):Boolean=db.withTransaction{
        val now=System.currentTimeMillis()
        val raw=RawItemEntity(item.url,item.source.id,item.title,item.summary,item.publishedAt,now,exactHash(item.title,item.url))
        if(dao.addRaw(raw)==-1L)return@withTransaction false
        val candidate=dao.recentEvents(now-14L*24*3600*1000).firstOrNull{isSameEvent(item,it)}
        if(candidate==null){
            val (importance,noise)=scores(item.title,item.source)
            val id=UUID.randomUUID().toString()
            val scope=if(item.source.groupName=="turkey"||item.source.groupName=="social"||item.title.contains("türkiye",true)||item.title.contains("turkey",true))"turkey" else "world"
            val verify=if(item.source.groupName=="official")4 else if(item.source.groupName=="social")1 else 2
            dao.putEvent(EventEntity(id,item.title,item.summary,scope,importance,noise,verify,0.0,1,now,now,"İlk kayıt",item.publishedAt))
            dao.link(EventItemEntity(id,item.url))
            dao.addVersion(EventVersionEntity(eventId=id,version=1,title=item.title,summary=item.summary,changeNote="İlk kayıt",createdAt=now))
        } else {
            val next=candidate.sourceCount+1
            val (i,n)=scores(item.title,item.source,next)
            val verification=maxOf(candidate.verification,if(item.source.groupName=="official")4 else if(item.source.groupName=="social")candidate.verification else if(next>=2)3 else 2)
            val eventPublished=listOfNotNull(candidate.publishedAt,item.publishedAt).maxOrNull()
            dao.putEvent(candidate.copy(
                title=item.title,
                summary=item.summary,
                importance=maxOf(candidate.importance,i),
                noise=minOf(candidate.noise,n),
                verification=verification,
                sourceCount=next,
                updatedAt=now,
                changeNote="Yeni kaynak eklendi",
                publishedAt=eventPublished
            ))
            dao.link(EventItemEntity(candidate.id,item.url))
            dao.addVersion(EventVersionEntity(eventId=candidate.id,version=next,title=item.title,summary=item.summary,changeNote="Yeni kaynak eklendi",createdAt=now))
        }
        true
    }
}
