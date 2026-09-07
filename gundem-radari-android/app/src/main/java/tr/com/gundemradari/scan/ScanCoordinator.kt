package tr.com.gundemradari.scan

import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.*
import tr.com.gundemradari.religion.ReligionTracker
import java.util.Locale
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
            val source=sources[index]
            result.onSuccess{items->
                for(item in items){
                    if(dao.rawExists(item.url))continue
                    val localized=translator.translateIfNeeded(item)
                    if(persist(localized))count++
                }
            }.onFailure{
                failures+=source.name
            }
        }

        dao.scan(
            ScanHistoryEntity(
                startedAt=started,
                finishedAt=System.currentTimeMillis(),
                newItems=count,
                failedSources=failures.size
            )
        )

        onProgress("$count yeni kayıt, ${failures.size} kaynak hatası")
        ScanOutcome(count,failures)
    }

    private fun turkeyFocused(title:String,summary:String):Boolean{
        val text=(" $title $summary ").lowercase(Locale("tr","TR"))
        val keys=listOf(
            " türkiye "," türk "," ankara "," istanbul "," erdoğan "," tbmm "," ak parti ",
            " chp "," mhp "," dem parti "," bakanlık "," tcmb "," afad "," diyanet ",
            " cumhurbaşkanı "," meclis "," yargıtay "," anayasa mahkemesi ",
            " ülke genelinde "," türkiye genelinde "," 81 il "
        )
        return keys.any{text.contains(it)}
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

        val religionPriority=ReligionTracker.priority(
            item.source,
            item.title,
            item.summary
        )

        val candidate=dao.recentEvents(
            now-14L*24*3600*1000
        ).firstOrNull{
            isSameEvent(item,it)
        }

        if(candidate==null){
            val (importance,noise)=scores(
                title=item.title,
                source=item.source,
                summary=item.summary
            )

            val id=UUID.randomUUID().toString()

            val scope=when{
                item.source.groupName=="religion_search"||
                item.source.groupName=="religion_direct"->"religion"

                turkeyFocused(item.title,item.summary)->"turkey"

                item.source.groupName=="turkey"->"turkey"

                item.source.groupName=="world_tr"->"world"

                item.source.groupName=="social"->
                    if(turkeyFocused(item.title,item.summary))"turkey"
                    else "world"

                else->"world"
            }

            dao.putEvent(
                EventEntity(
                    id=id,
                    title=item.title,
                    summary=item.summary,
                    scope=scope,
                    importance=importance,
                    noise=noise,
                    verification=0,
                    velocity=0.0,
                    sourceCount=1,
                    firstSeenAt=now,
                    updatedAt=now,
                    changeNote="İlk kayıt",
                    publishedAt=item.publishedAt,
                    religionPriority=religionPriority
                )
            )

            dao.link(
                EventItemEntity(
                    eventId=id,
                    rawUrl=item.url
                )
            )

            dao.addVersion(
                EventVersionEntity(
                    eventId=id,
                    version=1,
                    title=item.title,
                    summary=item.summary,
                    changeNote="İlk kayıt",
                    createdAt=now
                )
            )
        }else{
            val next=candidate.sourceCount+1

            val (importance,noise)=scores(
                title=item.title,
                source=item.source,
                sourceCount=next,
                summary=item.summary
            )

            val eventPublished=listOfNotNull(
                candidate.publishedAt,
                item.publishedAt
            ).maxOrNull()

            dao.putEvent(
                candidate.copy(
                    title=item.title,
                    summary=item.summary,
                    importance=maxOf(candidate.importance,importance),
                    noise=minOf(candidate.noise,noise),
                    verification=0,
                    sourceCount=next,
                    updatedAt=now,
                    changeNote="Yeni kaynak eklendi",
                    publishedAt=eventPublished,
                    religionPriority=maxOf(
                        candidate.religionPriority,
                        religionPriority
                    )
                )
            )

            dao.link(
                EventItemEntity(
                    eventId=candidate.id,
                    rawUrl=item.url
                )
            )

            dao.addVersion(
                EventVersionEntity(
                    eventId=candidate.id,
                    version=next,
                    title=item.title,
                    summary=item.summary,
                    changeNote="Yeni kaynak eklendi",
                    createdAt=now
                )
            )
        }

        true
    }
}
