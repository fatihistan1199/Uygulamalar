package tr.com.gundemradari.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tr.com.gundemradari.data.EventEnrichmentEntity
import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.data.GundemDao
import tr.com.gundemradari.data.ResearchItemRow
import tr.com.gundemradari.religion.RELIGION_MAX_AGE_DAYS
import tr.com.gundemradari.religion.ReligionTracker
import tr.com.gundemradari.web.ArticleReader
import tr.com.gundemradari.web.NewsWebSearch
import tr.com.gundemradari.web.ResearchedArticle
import tr.com.gundemradari.web.articleContentQuality
import tr.com.gundemradari.web.cleanResearchText
import tr.com.gundemradari.web.isUsefulResearchText
import tr.com.gundemradari.web.summarizeResearch
import tr.com.gundemradari.web.webSourceQuality
import java.util.concurrent.ConcurrentHashMap

data class EventResearchReport(
    val event:EventEntity,
    val whatHappened:List<String>,
    val whyImportant:List<String>,
    val latestSituation:List<String>,
    val displayArticles:List<ResearchedArticle>,
    val firstAt:Long?,
    val latestAt:Long?
)

class EventResearchService(
    private val dao:GundemDao,
    private val webSearch:NewsWebSearch=NewsWebSearch(),
    private val articleReader:ArticleReader=ArticleReader()
){
    private data class CachedReport(
        val createdAt:Long,
        val eventUpdatedAt:Long,
        val report:EventResearchReport
    )

    private data class LoadedArticle(
        val article:ResearchedArticle,
        val contentScore:Double
    )

    private val cache=ConcurrentHashMap<String,CachedReport>()

    suspend fun build(
        event:EventEntity,
        forceRefresh:Boolean=false
    ):EventResearchReport{
        val now=System.currentTimeMillis()
        val localEvent=!event.id.startsWith("websearch:")

        if(!forceRefresh){
            cache[event.id]?.let{cached->
                if(
                    cached.eventUpdatedAt==event.updatedAt &&
                    now-cached.createdAt<10L*60*1000
                ){
                    return cached.report
                }
            }

            if(localEvent){
                dao.enrichment(event.id)?.let{saved->
                    if(
                        saved.enrichedAt>=event.updatedAt &&
                        saved.shortSummary.isNotBlank()
                    ){
                        val report=reportFromSaved(event,saved)
                        cache[event.id]=CachedReport(now,event.updatedAt,report)
                        return report
                    }
                }
            }
        }

        val report=withContext(Dispatchers.Default){
            buildFresh(event)
        }

        if(localEvent && report.whatHappened.isNotEmpty()){
            persist(event,report)
        }

        cache[event.id]=CachedReport(
            createdAt=now,
            eventUpdatedAt=event.updatedAt,
            report=report
        )
        trimCache()
        return report
    }

    suspend fun enrichIfNeeded(eventId:String):Boolean{
        val event=dao.event(eventId)?:return false
        val saved=dao.enrichment(eventId)

        if(
            saved!=null &&
            saved.enrichedAt>=event.updatedAt &&
            saved.shortSummary.isNotBlank()
        ){
            return false
        }

        val report=build(event,forceRefresh=true)
        return report.whatHappened.isNotEmpty()
    }

    private suspend fun buildFresh(event:EventEntity):EventResearchReport{
        val rows=dao.researchItems(event.id)
        val times=rows.map{it.publishedAt?:it.firstSeenAt}
        val rankedRows=rows
            .distinctBy{it.url}
            .sortedWith(
                compareByDescending<ResearchItemRow>{it.trust}
                    .thenByDescending{it.summary.length}
                    .thenByDescending{it.publishedAt?:it.firstSeenAt}
            )

        var best:LoadedArticle?=null

        // Teyit için paralel çoklu okuma yok. İlk güçlü yayıncı sayfası yeterliyse dur.
        for(row in rankedRows.take(2)){
            val loaded=loadRow(row)
            if(best==null || loaded.contentScore>best!!.contentScore){
                best=loaded
            }
            if(loaded.contentScore>=55.0)break
        }

        val query=rankedRows
            .firstOrNull{it.originalTitle.isNotBlank()}
            ?.originalTitle
            ?:event.title

        // Mevcut kaynaklar içerik vermediyse web araması yalnız teknik yedek olarak devreye girer.
        if(best==null || best!!.contentScore<28.0){
            val webResults=runCatching{
                webSearch.search(
                    query=query,
                    limit=4,
                    expandDescriptions=false,
                    maxAgeDays=if(event.scope=="religion" || event.religionPriority>0){
                        RELIGION_MAX_AGE_DAYS
                    }else null
                )
            }.getOrElse{emptyList()}

            for(row in webResults.take(2)){
                val details=articleReader.read(row.url)
                val contentScore=details?.let(::articleContentQuality)?:0.0

                val article=if(details!=null){
                    val sourceQuality=webSourceQuality(
                        row.source.ifBlank{details.host},
                        details.finalUrl.ifBlank{row.url}
                    )*100.0
                    ResearchedArticle(
                        sourceName=row.source.ifBlank{details.host.ifBlank{"Web kaynağı"}},
                        title=details.title.ifBlank{row.title},
                        url=details.finalUrl.ifBlank{row.url},
                        description=cleanResearchText(details.description)
                            .takeIf(::isUsefulResearchText)
                            ?:cleanResearchText(row.snippet)
                                .takeIf(::isUsefulResearchText)
                            ?:"",
                        paragraphs=details.paragraphs,
                        publishedAt=row.publishedAt,
                        isPrimary=true,
                        quality=(sourceQuality*0.30+contentScore*0.70)
                            .coerceIn(0.0,100.0)
                    )
                }else{
                    ResearchedArticle(
                        sourceName=row.source.ifBlank{"Web kaynağı"},
                        title=row.title,
                        url=row.url,
                        description=cleanResearchText(row.snippet)
                            .takeIf(::isUsefulResearchText)
                            ?:"",
                        paragraphs=emptyList(),
                        publishedAt=row.publishedAt,
                        isPrimary=true,
                        quality=webSourceQuality(row.source,row.url)*100.0
                    )
                }

                val candidate=LoadedArticle(article,contentScore)
                if(best==null || candidate.contentScore>best!!.contentScore){
                    best=candidate
                }
                if(contentScore>=55.0)break
            }
        }

        if(best==null && rankedRows.isNotEmpty()){
            best=LoadedArticle(
                rowFallback(rankedRows.first()),
                8.0
            )
        }

        val articles=listOfNotNull(best?.article)
        val summary=summarizeResearch(
            eventTitle=event.title,
            eventSummary=event.summary,
            articles=articles
        )

        return EventResearchReport(
            event=event,
            whatHappened=summary.whatHappened,
            whyImportant=summary.whyImportant,
            latestSituation=summary.latestSituation,
            displayArticles=articles.take(1)+personAccountLinks(event),
            firstAt=times.minOrNull(),
            latestAt=times.maxOrNull()
        )
    }

    private suspend fun loadRow(row:ResearchItemRow):LoadedArticle{
        val details=articleReader.read(row.url)

        if(details==null){
            return LoadedArticle(
                article=rowFallback(row),
                contentScore=if(isUsefulResearchText(row.summary))10.0 else 0.0
            )
        }

        val contentScore=articleContentQuality(details)
        val sourceQuality=(row.trust*100.0).coerceIn(0.0,100.0)

        return LoadedArticle(
            article=ResearchedArticle(
                sourceName=row.sourceName,
                title=details.title.ifBlank{row.title},
                url=details.finalUrl.ifBlank{row.url},
                description=cleanResearchText(details.description)
                    .takeIf(::isUsefulResearchText)
                    ?:cleanResearchText(row.summary)
                        .takeIf(::isUsefulResearchText)
                    ?:"",
                paragraphs=details.paragraphs,
                publishedAt=row.publishedAt,
                isPrimary=true,
                quality=(sourceQuality*0.30+contentScore*0.70)
                    .coerceIn(0.0,100.0)
            ),
            contentScore=contentScore
        )
    }

    private fun rowFallback(row:ResearchItemRow)=ResearchedArticle(
        sourceName=row.sourceName,
        title=row.title,
        url=row.url,
        description=cleanResearchText(row.summary)
            .takeIf(::isUsefulResearchText)
            ?:"",
        paragraphs=emptyList(),
        publishedAt=row.publishedAt,
        isPrimary=true,
        quality=(row.trust*100.0).coerceIn(0.0,100.0)
    )

    private suspend fun persist(
        event:EventEntity,
        report:EventResearchReport
    ){
        val source=report.displayArticles.firstOrNull{it.quality>=0.0}
        val shortSummary=(
            report.whatHappened.joinToString(" ")
                .ifBlank{source?.description.orEmpty()}
                .ifBlank{event.summary}
        )
            .replace(Regex("\\s+")," ")
            .trim()
            .take(460)

        if(shortSummary.isBlank())return

        val quality=source?.quality?:event.bestContentQuality
        val now=System.currentTimeMillis()

        dao.putEnrichment(
            EventEnrichmentEntity(
                eventId=event.id,
                sourceName=source?.sourceName.orEmpty(),
                sourceUrl=source?.url.orEmpty(),
                sourceTitle=source?.title.orEmpty(),
                shortSummary=shortSummary,
                whatHappened=pack(report.whatHappened),
                whyImportant=pack(report.whyImportant),
                latestSituation=pack(report.latestSituation),
                contentQuality=quality,
                enrichedAt=now
            )
        )

        dao.updateEnrichedSummary(
            eventId=event.id,
            summary=shortSummary,
            quality=quality
        )
    }

    private suspend fun reportFromSaved(
        event:EventEntity,
        saved:EventEnrichmentEntity
    ):EventResearchReport{
        val rows=dao.researchItems(event.id)
        val times=rows.map{it.publishedAt?:it.firstSeenAt}
        val source=if(saved.sourceUrl.isNotBlank()){
            listOf(
                ResearchedArticle(
                    sourceName=saved.sourceName.ifBlank{"Kaynak"},
                    title=saved.sourceTitle,
                    url=saved.sourceUrl,
                    description=saved.shortSummary,
                    paragraphs=emptyList(),
                    publishedAt=null,
                    isPrimary=true,
                    quality=saved.contentQuality
                )
            )
        }else emptyList()

        return EventResearchReport(
            event=event.copy(summary=saved.shortSummary),
            whatHappened=unpack(saved.whatHappened),
            whyImportant=unpack(saved.whyImportant),
            latestSituation=unpack(saved.latestSituation),
            displayArticles=source+personAccountLinks(event),
            firstAt=times.minOrNull(),
            latestAt=times.maxOrNull()
        )
    }

    private fun personAccountLinks(event:EventEntity):List<ResearchedArticle>{
        if(event.scope!="religion" && event.religionPriority<=0)return emptyList()

        val profile=ReligionTracker.profileFor(event.title,event.summary)
            ?:return emptyList()

        return profile.links().map{(platform,url)->
            ResearchedArticle(
                sourceName="${profile.name} · $platform",
                title="Doğrudan kişi hesabı",
                url=url,
                description="",
                paragraphs=emptyList(),
                publishedAt=null,
                isPrimary=false,
                quality=-1.0
            )
        }
    }

    private fun pack(lines:List<String>):String=
        lines.map{it.trim()}.filter{it.isNotBlank()}.joinToString("\n")

    private fun unpack(value:String):List<String> =
        value.lineSequence().map{it.trim()}.filter{it.isNotBlank()}.toList()

    private fun trimCache(){
        if(cache.size<=32)return
        val oldest=cache.entries
            .sortedBy{it.value.createdAt}
            .take(cache.size-24)
        oldest.forEach{cache.remove(it.key)}
    }
}
