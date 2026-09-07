package tr.com.gundemradari.religion

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tr.com.gundemradari.data.SourceEntity
import tr.com.gundemradari.scan.FetchedItem
import tr.com.gundemradari.web.NewsWebSearch

class ReligionWebWatcher(private val search:NewsWebSearch=NewsWebSearch()){
    suspend fun fetch(source:SourceEntity):List<FetchedItem> = coroutineScope{
        ReligionTracker.searchQueries.map{query->
            async{
                search.search(query,limit=6,expandDescriptions=false)
                    .filter{ReligionTracker.matches(it.title,it.snippet)}
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
}
