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
                search.search(
                    query=query,
                    limit=8,
                    expandDescriptions=false,
                    maxAgeDays=RELIGION_MAX_AGE_DAYS
                )
                    .filter{row->
                        ReligionTracker.isFresh(row.publishedAt) &&
                        ReligionWatchEngine.evaluate(
                            source,row.title,row.snippet
                        ).accepted
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
            .sortedByDescending{it.publishedAt?:0L}
            .take(45)
    }

    suspend fun fetchYouTube(source:SourceEntity):List<FetchedItem> = coroutineScope{
        ReligionTracker.profiles.map{profile->
            async{
                youtube.search(profile,limit=5)
                    .filter{row->ReligionTracker.isFresh(row.publishedAt)}
                    .map{row->
                        val taggedTitle="${profile.name}: ${row.title}"
                        FetchedItem(
                            source=source,
                            url=row.url,
                            title=taggedTitle,
                            summary="Doğrudan YouTube · ${profile.name}",
                            publishedAt=row.publishedAt,
                            originalTitle=row.title,
                            originalSummary="Doğrudan YouTube · ${profile.name}"
                        )
                    }
            }
        }.awaitAll().flatten()
            .distinctBy{it.url}
            .sortedByDescending{it.publishedAt?:0L}
            .take(30)
    }
}
