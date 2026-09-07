package tr.com.gundemradari.web

import tr.com.gundemradari.scan.FetchedItem

class TurkishCoverageResolver(private val search:NewsWebSearch=NewsWebSearch()){
    suspend fun improve(original:FetchedItem,translated:FetchedItem):FetchedItem{
        val rows=search.search(original.originalTitle,limit=5,expandDescriptions=true)
        val best=rows.firstOrNull{looksTurkish(it.title)} ?: return translated
        val betterSummary=rows.asSequence()
            .map{it.snippet}
            .firstOrNull{it.length>=70 && looksTurkish(it)}
        return translated.copy(
            title=best.title,
            summary=betterSummary ?: translated.summary
        )
    }
}
