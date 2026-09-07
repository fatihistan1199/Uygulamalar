package tr.com.gundemradari.research

import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.data.GundemDao
import tr.com.gundemradari.data.ResearchItemRow
import java.util.Locale

data class EventResearchReport(
    val event:EventEntity,
    val sources:List<ResearchItemRow>,
    val independentSourceCount:Int,
    val officialSourceCount:Int,
    val socialSourceCount:Int,
    val commonTerms:List<String>,
    val firstAt:Long?,
    val latestAt:Long?
)

class EventResearchService(private val dao:GundemDao){
    private val stopWords=setOf(
        "olan","olarak","için","ile","ama","ancak","daha","sonra","önce","gibi","kadar","üzerine",
        "arasında","karşı","son","yeni","göre","dedi","diyor","etti","ediyor","oldu","oluyor","olacak",
        "this","that","with","from","about","after","before","over","under","into","during","their","they",
        "have","has","will","would","could","should","news"
    )

    suspend fun build(event:EventEntity):EventResearchReport{
        val rows=dao.researchItems(event.id)
        val distinctSources=rows.distinctBy{it.sourceName}
        val common=commonTerms(distinctSources)
        val times=rows.map{it.publishedAt?:it.firstSeenAt}
        return EventResearchReport(
            event=event,
            sources=rows,
            independentSourceCount=distinctSources.size,
            officialSourceCount=distinctSources.count{it.groupName=="official"},
            socialSourceCount=distinctSources.count{it.groupName=="social"},
            commonTerms=common,
            firstAt=times.minOrNull(),
            latestAt=times.maxOrNull()
        )
    }

    private fun commonTerms(rows:List<ResearchItemRow>):List<String>{
        if(rows.size<2)return emptyList()
        val locale=Locale("tr","TR")
        val counts=mutableMapOf<String,Int>()
        rows.forEach{row->
            val words=(row.title+" "+row.summary)
                .lowercase(locale)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter{it.length>=4 && it !in stopWords && it.none(Char::isDigit)}
                .toSet()
            words.forEach{counts[it]=(counts[it]?:0)+1}
        }
        val threshold=maxOf(2,(rows.size+1)/2)
        return counts.entries
            .filter{it.value>=threshold}
            .sortedWith(compareByDescending<Map.Entry<String,Int>>{it.value}.thenByDescending{it.key.length})
            .take(8)
            .map{it.key}
    }
}
