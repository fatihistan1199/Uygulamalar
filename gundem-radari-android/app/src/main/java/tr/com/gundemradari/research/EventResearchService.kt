package tr.com.gundemradari.research

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.data.GundemDao
import tr.com.gundemradari.web.ArticleReader
import tr.com.gundemradari.web.NewsWebSearch
import tr.com.gundemradari.web.ResearchedArticle
import tr.com.gundemradari.web.summarizeResearch
import tr.com.gundemradari.web.webSourceQuality
import tr.com.gundemradari.web.cleanResearchText
import tr.com.gundemradari.web.isUsefulResearchText
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

    private val cache=ConcurrentHashMap<String,CachedReport>()

    suspend fun build(event:EventEntity):EventResearchReport{
        val now=System.currentTimeMillis()
        cache[event.id]?.let{cached->
            if(
                cached.eventUpdatedAt==event.updatedAt &&
                now-cached.createdAt<10L*60*1000
            ){
                return cached.report
            }
        }

        val report=withContext(Dispatchers.Default){
            coroutineScope{
        val rows=dao.researchItems(event.id)
        val times=rows.map{it.publishedAt?:it.firstSeenAt}
        val bestRows=rows.sortedByDescending{it.trust}
        val query=bestRows.firstOrNull{it.originalTitle.isNotBlank()}?.originalTitle ?: event.title

        val primaryArticles=bestRows.distinctBy{it.url}.take(3).mapIndexed{index,row->
            async{
                articleReader.read(row.url)?.let{d->
                    val wrapperSource=
                        row.groupName=="religion_search" ||
                        row.sourceName.contains("Google News",ignoreCase=true) ||
                        row.sourceName.contains("YouTube",ignoreCase=true)

                    ResearchedArticle(
                        sourceName=if(wrapperSource){
                            d.host.ifBlank{row.sourceName}
                        }else{
                            row.sourceName
                        },
                        title=d.title.ifBlank{row.title},
                        url=d.finalUrl.ifBlank{row.url},
                        description=cleanResearchText(d.description)
                            .takeIf(::isUsefulResearchText)
                            ?: cleanResearchText(row.summary)
                                .takeIf(::isUsefulResearchText)
                            ?: "",
                        paragraphs=d.paragraphs,
                        publishedAt=row.publishedAt,
                        isPrimary=index==0,
                        quality=row.trust
                    )
                } ?: ResearchedArticle(
                    sourceName=row.sourceName,title=row.title,url=row.url,
                    description=cleanResearchText(row.summary)
                        .takeIf(::isUsefulResearchText)
                        ?: "",
                    paragraphs=emptyList(),publishedAt=row.publishedAt,
                    isPrimary=index==0,quality=row.trust
                )
            }
        }.awaitAll()

        val webResults=runCatching{
            webSearch.search(query,limit=7,expandDescriptions=false)
        }.getOrElse{emptyList()}

        val webArticles=webResults
            .filter{wr->primaryArticles.none{it.title.equals(wr.title,true)}}
            .take(3)
            .map{wr->
                async{
                    articleReader.read(wr.url)?.let{d->
                        ResearchedArticle(
                            sourceName=wr.source.ifBlank{d.host.ifBlank{"Web kaynağı"}},
                            title=d.title.ifBlank{wr.title},
                            url=d.finalUrl.ifBlank{wr.url},
                            description=cleanResearchText(d.description)
                                .takeIf(::isUsefulResearchText)
                                ?: cleanResearchText(wr.snippet)
                                    .takeIf(::isUsefulResearchText)
                                ?: "",
                            paragraphs=d.paragraphs,
                            publishedAt=wr.publishedAt,
                            isPrimary=false,
                            quality=webSourceQuality(
                                wr.source.ifBlank{d.host},
                                d.finalUrl.ifBlank{wr.url}
                            )
                        )
                    } ?: ResearchedArticle(
                        sourceName=wr.source.ifBlank{"Web kaynağı"},title=wr.title,url=wr.url,
                        description=cleanResearchText(wr.snippet)
                            .takeIf(::isUsefulResearchText)
                            ?: "",
                        paragraphs=emptyList(),publishedAt=wr.publishedAt,
                        isPrimary=false,
                        quality=webSourceQuality(wr.source,wr.url)
                    )
                }
            }.awaitAll()

        val allArticles=(primaryArticles+webArticles)
            .filter{
                it.title.isNotBlank() ||
                it.description.isNotBlank() ||
                it.paragraphs.isNotEmpty()
            }
            .distinctBy{it.url}
            .take(7)

        val summary=summarizeResearch(
            eventTitle=event.title,
            eventSummary=event.summary,
            articles=allArticles
        )

        val realPublisherArticles=allArticles.filterNot{
            val name=it.sourceName.lowercase()
            name.contains("din takip kişileri") ||
            name.contains("google news")
        }
        val displayPool=if(realPublisherArticles.size>=2){
            realPublisherArticles
        }else{
            allArticles
        }

        val visible=displayPool
            .sortedWith(
                compareByDescending<ResearchedArticle>{it.quality}
                    .thenByDescending{it.description.length+it.paragraphs.sumOf{p->p.length}}
            )
            .distinctBy{it.sourceName.lowercase()}
            .take(2)
            .mapIndexed{index,a->a.copy(isPrimary=index==0)}

        EventResearchReport(
            event=event,
            whatHappened=summary.whatHappened,
            whyImportant=summary.whyImportant,
            latestSituation=summary.latestSituation,
            displayArticles=visible,
            firstAt=times.minOrNull(),
            latestAt=times.maxOrNull()
        )
            }
        }

        cache[event.id]=CachedReport(
            createdAt=now,
            eventUpdatedAt=event.updatedAt,
            report=report
        )

        if(cache.size>32){
            val oldest=cache.entries
                .sortedBy{it.value.createdAt}
                .take(cache.size-24)
            oldest.forEach{cache.remove(it.key)}
        }

        return report
    }
}
