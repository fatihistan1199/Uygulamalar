package tr.com.gundemradari.religion

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.SourceEntity
import tr.com.gundemradari.scan.FetchedItem
import tr.com.gundemradari.web.NewsWebSearch

class ReligionWebWatcher(
    private val search:NewsWebSearch=NewsWebSearch(),
    private val youtube:YouTubePersonSearch=YouTubePersonSearch()
){
    suspend fun fetchGoogleNews(source:SourceEntity):List<FetchedItem> = coroutineScope{
        ReligionTracker.searchQueries.map{query->
            async{
                search.search(query,limit=7,expandDescriptions=false)
                    .filter{row->
                        ReligionWatchEngine.evaluate(source,row.title,row.snippet).accepted
                    }
                    .map{row->
                        FetchedItem(
                            source=source,
                            url=row.url,
                            title=row.title,
                            summary=row.snippet,
                            publishedAt=row.publishedAt,
                            originalTitle=row.title,
                            originalSummary=row.snippet
                        )
                    }
            }
        }.awaitAll().flatten()
            .distinctBy{it.url}
            .take(45)
    }

    suspend fun fetchYouTube(source:SourceEntity):List<FetchedItem> = coroutineScope{
        ReligionTracker.personQueries.map{person->
            async{
                youtube.search(person,limit=4)
                    .filter{row->
                        ReligionWatchEngine.evaluate(source,row.title,"").accepted
                    }
                    .map{row->
                        FetchedItem(
                            source=source,
                            url=row.url,
                            title=row.title,
                            summary="YouTube · $person",
                            publishedAt=null,
                            originalTitle=row.title,
                            originalSummary="YouTube · $person"
                        )
                    }
            }
        }.awaitAll().flatten()
            .distinctBy{it.url}
            .take(24)
    }
}
