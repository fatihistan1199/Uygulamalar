package tr.com.gundemradari.scan

import tr.com.gundemradari.data.SourceEntity
import kotlin.math.max
import kotlin.math.min

fun sourceQuality(source:SourceEntity):Double{
    val adjustment=when(source.groupName){
        "official"->0.08
        "religion_direct"->0.03
        "world_tr"->0.02
        "social"->-0.18
        "religion_search"->-0.08
        else->0.0
    }
    return (source.trust+adjustment).coerceIn(0.20,1.0)
}

fun contentQuality(item:FetchedItem):Double{
    var score=sourceQuality(item.source)*68.0
    val title=item.title.trim()
    val summary=item.summary.trim()

    score+=when(title.length){
        in 45..150->16.0
        in 25..200->10.0
        else->3.0
    }
    score+=when(summary.length){
        in 100..650->16.0
        in 50..900->9.0
        else->2.0
    }

    val lower=(title+" "+summary).lowercase()
    val clickbait=listOf("şoke eden","görenler inanamadı","bakın ne yaptı","çok konuşulacak","olay oldu","sosyal medya bunu konuşuyor")
    if(clickbait.any{lower.contains(it)})score-=18.0
    if(lower.contains("[obj]")||lower.contains("[object object]"))score-=25.0
    if(title.count{it=='!'}>=2)score-=5.0

    return min(100.0,max(0.0,score))
}
