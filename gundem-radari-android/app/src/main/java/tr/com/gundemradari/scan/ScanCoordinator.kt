package tr.com.gundemradari.scan

import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.*
import tr.com.gundemradari.religion.ReligionTracker
import java.util.UUID

data class ScanOutcome(val newItems:Int,val failed:List<String>)

class ScanCoordinator(private val db:AppDatabase){
    private val dao=db.dao()
    private val client=FeedClient()
    private val translator=NewsTranslator()

    suspend fun scan(onProgress:(String)->Unit):ScanOutcome=coroutineScope{
        val started=System.currentTimeMillis()
        val sources=dao.enabledSources()
        val touchedEvents=linkedSetOf<String>()

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
                    val cleaned=localized.copy(
                        title=sanitizeNewsText(localized.title),
                        summary=sanitizeNewsText(localized.summary),
                        originalTitle=sanitizeNewsText(localized.originalTitle),
                        originalSummary=sanitizeNewsText(localized.originalSummary)
                    )

                    persist(cleaned)?.let{eventId->
                        count++
                        touchedEvents+=eventId
                    }
                }
            }.onFailure{
                failures+=source.name
            }
        }

        if(touchedEvents.isNotEmpty()){
            onProgress("Haberler yeniden sınıflandırılıyor")
            reviewCategorization(touchedEvents)
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

    private suspend fun reviewCategorization(eventIds:Set<String>){
        eventIds.forEach{eventId->
            val event=dao.event(eventId) ?: return@forEach
            val items=dao.classificationItems(eventId)
            val decision=EventClassifier.classify(event,items)

            if(decision.scope!=event.scope){
                dao.updateEventScope(eventId,decision.scope)
            }
        }
    }

    private suspend fun persist(item:FetchedItem):String?=db.withTransaction{
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

        if(dao.addRaw(raw)==-1L)return@withTransaction null

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

            val provisional=EventEntity(
                id=id,
                title=item.title,
                summary=item.summary,
                scope="world",
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

            val decision=EventClassifier.classify(
                provisional,
                listOf(
                    ClassificationItemRow(
                        sourceId=item.source.id,
                        groupName=item.source.groupName,
                        title=item.title,
                        summary=item.summary
                    )
                )
            )

            dao.putEvent(
                provisional.copy(scope=decision.scope)
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

            id
        }else{
            val alreadyHasSource=dao.eventHasSource(
                candidate.id,
                item.source.id
            )

            val nextSourceCount=
                candidate.sourceCount + if(alreadyHasSource)0 else 1

            val (importance,noise)=scores(
                title=item.title,
                source=item.source,
                sourceCount=nextSourceCount,
                summary=item.summary
            )

            val eventPublished=listOfNotNull(
                candidate.publishedAt,
                item.publishedAt
            ).maxOrNull()

            val incomingBetterHeadline=
                importance>candidate.importance+4 ||
                candidate.title.length<35 ||
                candidate.title.contains("[OBJ]",true)

            dao.putEvent(
                candidate.copy(
                    title=if(incomingBetterHeadline)item.title else candidate.title,
                    summary=if(incomingBetterHeadline&&item.summary.isNotBlank())item.summary else candidate.summary,
                    importance=maxOf(candidate.importance,importance),
                    noise=minOf(candidate.noise,noise),
                    verification=0,
                    sourceCount=nextSourceCount,
                    updatedAt=now,
                    changeNote=if(alreadyHasSource)"Yeni gelişme" else "Yeni kaynak eklendi",
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

            val nextVersion=(dao.maxEventVersion(candidate.id)?:1)+1

            dao.addVersion(
                EventVersionEntity(
                    eventId=candidate.id,
                    version=nextVersion,
                    title=item.title,
                    summary=item.summary,
                    changeNote=if(alreadyHasSource)"Yeni gelişme" else "Yeni kaynak eklendi",
                    createdAt=now
                )
            )

            candidate.id
        }
    }
}
