package tr.com.gundemradari.research

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.data.GundemDao
import tr.com.gundemradari.web.ArticleReader
import tr.com.gundemradari.web.NewsWebSearch
import tr.com.gundemradari.web.ResearchedArticle
import tr.com.gundemradari.web.summarizeResearch

data class EventResearchReport(
    val event:EventEntity,
    val whatHappened:List<String>,
    val details:List<String>,
    val background:List<String>,
    val displayArticles:List<ResearchedArticle>,
    val firstAt:Long?,
    val latestAt:Long?
)

class EventResearchService(
    private val dao:GundemDao,
    private val webSearch:NewsWebSearch=NewsWebSearch(),
    private val articleReader:ArticleReader=ArticleReader()
){
    suspend fun build(event:EventEntity):EventResearchReport=coroutineScope{
        val rows=dao.researchItems(event.id)
        val times=rows.map{it.publishedAt?:it.firstSeenAt}
        val query=rows.firstOrNull{it.originalTitle.isNotBlank()}?.originalTitle ?: event.title

        val primaryArticles=rows.distinctBy{it.url}.take(3).mapIndexed{index,row->
            async{
                articleReader.read(row.url)?.let{d->
                    ResearchedArticle(
                        sourceName=row.sourceName,
                        title=d.title.ifBlank{row.title},
                        url=d.finalUrl.ifBlank{row.url},
                        description=d.description.ifBlank{row.summary},
                        paragraphs=d.paragraphs,
                        publishedAt=row.publishedAt,
                        isPrimary=index==0
                    )
                } ?: ResearchedArticle(
                    sourceName=row.sourceName,
                    title=row.title,
                    url=row.url,
                    description=row.summary,
                    paragraphs=emptyList(),
                    publishedAt=row.publishedAt,
                    isPrimary=index==0
                )
            }
        }.awaitAll()

        val webResults=runCatching{
            webSearch.search(query,limit=10,expandDescriptions=false)
        }.getOrElse{emptyList()}

        val webArticles=webResults
            .filter{wr->primaryArticles.none{it.title.equals(wr.title,true)}}
            .take(6)
            .map{wr->
                async{
                    articleReader.read(wr.url)?.let{d->
                        ResearchedArticle(
                            sourceName=wr.source.ifBlank{d.host.ifBlank{"Web kaynağı"}},
                            title=d.title.ifBlank{wr.title},
                            url=d.finalUrl.ifBlank{wr.url},
                            description=d.description.ifBlank{wr.snippet},
                            paragraphs=d.paragraphs,
                            publishedAt=wr.publishedAt,
                            isPrimary=false
                        )
                    } ?: ResearchedArticle(
                        sourceName=wr.source.ifBlank{"Web kaynağı"},
                        title=wr.title,
                        url=wr.url,
                        description=wr.snippet,
                        paragraphs=emptyList(),
                        publishedAt=wr.publishedAt,
                        isPrimary=false
                    )
                }
            }.awaitAll()

        val allArticles=(primaryArticles+webArticles)
            .filter{it.title.isNotBlank()||it.description.isNotBlank()||it.paragraphs.isNotEmpty()}
            .distinctBy{it.url}
            .take(9)

        val summary=summarizeResearch(event.title,allArticles)

        val visible=allArticles
            .sortedWith(
                compareByDescending<ResearchedArticle>{it.isPrimary}
                    .thenByDescending{it.description.length+it.paragraphs.sumOf{p->p.length}}
            )
            .distinctBy{it.sourceName.lowercase()}
            .take(2)

        EventResearchReport(
            event=event,
            whatHappened=summary.whatHappened,
            details=summary.details,
            background=summary.background,
            displayArticles=visible,
            firstAt=times.minOrNull(),
            latestAt=times.maxOrNull()
        )
    }
}
