package tr.com.gundemradari.scan

import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import tr.com.gundemradari.data.*
import tr.com.gundemradari.religion.ReligionWatchEngine
import java.util.UUID

data class ScanOutcome(val newItems:Int,val failed:List<String>)

class ScanCoordinator(private val db:AppDatabase){
    private val dao=db.dao()
    private val client=FeedClient()
    private val translator=NewsTranslator()
    private val maintenanceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)

    suspend fun scan(onProgress:(String)->Unit,waitForReview:Boolean=false):ScanOutcome{
        if(!scanMutex.tryLock())return ScanOutcome(0,emptyList())

        try{
            return coroutineScope{
                val started=System.currentTimeMillis()
                val sources=dao.scanSources(started)
                val touchedEvents=linkedSetOf<String>()
                val failures=mutableListOf<String>()
                var count=0

                onProgress("${sources.size} kaynak taranıyor")

                val outcomes=sources.map{s->async{s to runCatching{client.fetch(s)}}}.awaitAll()

                outcomes.forEach{(source,result)->
                    result.onSuccess{items->
                        markSourceSuccess(source.id)
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
                        markSourceFailure(source.id)
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

                if(touchedEvents.isNotEmpty()){
                    if(waitForReview)reviewCategorizationSlowly(touchedEvents)
                    else scheduleBackgroundReview(touchedEvents)
                }

                onProgress("$count yeni kayıt")
                ScanOutcome(count,failures)
            }
        }finally{
            scanMutex.unlock()
        }
    }

    private suspend fun markSourceSuccess(sourceId:String){
        val old=dao.sourceHealth(sourceId)
        dao.putSourceHealth(
            SourceHealthEntity(
                sourceId=sourceId,
                consecutiveFailures=0,
                lastFailureAt=old?.lastFailureAt,
                lastSuccessAt=System.currentTimeMillis(),
                cooldownUntil=0L
            )
        )
    }

    private suspend fun markSourceFailure(sourceId:String){
        val now=System.currentTimeMillis()
        val old=dao.sourceHealth(sourceId)
        val failures=(old?.consecutiveFailures?:0)+1
        val cooldown=when(failures){
            1->10L*60*1000
            2->30L*60*1000
            3->2L*60*60*1000
            else->6L*60*60*1000
        }
        dao.putSourceHealth(
            SourceHealthEntity(
                sourceId=sourceId,
                consecutiveFailures=failures,
                lastFailureAt=now,
                lastSuccessAt=old?.lastSuccessAt,
                cooldownUntil=now+cooldown
            )
        )
    }

    private fun scheduleBackgroundReview(eventIds:Set<String>){
        maintenanceScope.launch{
            delay(1200)
            reviewCategorizationSlowly(eventIds)
        }
    }

    private suspend fun reviewCategorizationSlowly(eventIds:Set<String>){
        eventIds.toList().chunked(4).forEach{batch->
            batch.forEach{eventId->
                val event=dao.event(eventId) ?: return@forEach
                val items=dao.classificationItems(eventId)
                val decision=EventClassifier.classify(event,items)
                if(decision.scope!=event.scope || decision.topic!=event.topic){
                    dao.updateEventClassification(eventId,decision.scope,decision.topic)
                }
                yield()
            }
            delay(180)
        }
    }

    private suspend fun persist(item:FetchedItem):String?=db.withTransaction{
        val now=System.currentTimeMillis()
        val religionMatch=ReligionWatchEngine.evaluate(item.source,item.title,item.summary)
        val dedicatedReligionSource=
            item.source.groupName=="religion_search" || item.source.groupName=="religion_direct"

        if(dedicatedReligionSource && !religionMatch.accepted){
            return@withTransaction null
        }

        val religionPriority=if(religionMatch.accepted)religionMatch.priority else 0
        val incomingQuality=contentQuality(item)

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

        val incomingText=item.title+" "+item.summary.take(320)
        val candidate=dao.recentEvents(now-14L*24*3600*1000)
            .asSequence()
            .filter{isSameEvent(item,it)}
            .map{event->
                event to similarity(
                    incomingText,
                    event.title+" "+event.summary.take(320)
                )
            }
            .maxByOrNull{it.second}
            ?.first

        if(candidate==null){
            val (importance,noise)=scores(item.title,item.source,summary=item.summary)
            val id=UUID.randomUUID().toString()

            val provisional=EventEntity(
                id=id,title=item.title,summary=item.summary,scope="world",
                importance=importance,noise=noise,verification=0,velocity=0.0,
                sourceCount=1,firstSeenAt=now,updatedAt=now,changeNote="İlk kayıt",
                publishedAt=item.publishedAt,religionPriority=religionPriority,
                topic="general",bestContentQuality=incomingQuality
            )

            val decision=EventClassifier.classify(
                provisional,
                listOf(ClassificationItemRow(item.source.id,item.source.groupName,item.title,item.summary))
            )

            dao.putEvent(provisional.copy(scope=decision.scope,topic=decision.topic))
            dao.link(EventItemEntity(id,item.url))
            dao.addVersion(
                EventVersionEntity(
                    eventId=id,version=1,title=item.title,summary=item.summary,
                    changeNote="İlk kayıt",createdAt=now
                )
            )
            id
        }else{
            val alreadyHasSource=dao.eventHasSource(candidate.id,item.source.id)
            val existingFamilies=dao.eventSourceIds(candidate.id)
                .map(::sourceFamily)
                .toSet()
            val nextSourceCount=(existingFamilies+sourceFamily(item.source.id)).size
            val (importance,noise)=scores(item.title,item.source,nextSourceCount,item.summary)
            val eventPublished=listOfNotNull(candidate.publishedAt,item.publishedAt).maxOrNull()

            val useIncoming=
                incomingQuality>candidate.bestContentQuality+1.5 ||
                candidate.title.contains("[OBJ]",true) ||
                candidate.summary.contains("[OBJ]",true)

            dao.putEvent(
                candidate.copy(
                    title=if(useIncoming)item.title else candidate.title,
                    summary=if(useIncoming&&item.summary.isNotBlank())item.summary else candidate.summary,
                    importance=maxOf(candidate.importance,importance),
                    noise=minOf(candidate.noise,noise),
                    verification=0,
                    sourceCount=nextSourceCount,
                    updatedAt=now,
                    changeNote=if(alreadyHasSource)"Yeni gelişme" else "Yeni kaynak eklendi",
                    publishedAt=eventPublished,
                    religionPriority=maxOf(candidate.religionPriority,religionPriority),
                    bestContentQuality=maxOf(candidate.bestContentQuality,incomingQuality)
                )
            )

            dao.link(EventItemEntity(candidate.id,item.url))
            val nextVersion=(dao.maxEventVersion(candidate.id)?:1)+1
            dao.addVersion(
                EventVersionEntity(
                    eventId=candidate.id,version=nextVersion,title=item.title,summary=item.summary,
                    changeNote=if(alreadyHasSource)"Yeni gelişme" else "Yeni kaynak eklendi",
                    createdAt=now
                )
            )
            candidate.id
        }
    }

    companion object{private val scanMutex=Mutex()}
}
