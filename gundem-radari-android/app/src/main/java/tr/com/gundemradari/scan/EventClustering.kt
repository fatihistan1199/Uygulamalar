package tr.com.gundemradari.scan

import tr.com.gundemradari.data.EventEntity
import java.util.Locale
import kotlin.math.abs

private val clusterLocale=Locale("tr","TR")

private val clusterStopWords=setOf(
    "ve","ile","için","ama","fakat","ancak","bir","bu","şu","o","da","de","mi","mı","mu","mü",
    "son","dakika","yeni","haber","haberi","açıkladı","dedi","oldu","oluyor","olacak","sonrası",
    "üzerine","göre","karşı","hakkında","ardından","nedeniyle","nedeni","ise"
)

private fun tokens(text:String):Set<String> =
    text.lowercase(clusterLocale)
        .replace(Regex("""\[(?:obj|object)\]""",RegexOption.IGNORE_CASE)," ")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter{it.length>2 && it !in clusterStopWords}
        .toSet()

private fun jaccard(a:Set<String>,b:Set<String>):Double =
    if(a.isEmpty()||b.isEmpty())0.0
    else a.intersect(b).size.toDouble()/a.union(b).size

private fun overlap(a:Set<String>,b:Set<String>):Double =
    if(a.isEmpty()||b.isEmpty())0.0
    else a.intersect(b).size.toDouble()/minOf(a.size,b.size)

private fun earthquakeMagnitude(text:String):Double?{
    val lower=text.lowercase(clusterLocale)
    val patterns=listOf(
        Regex("""\b(?:mw|ml|mb|m)\s*[:=]?\s*(\d{1,2}(?:[.,]\d)?)\b"""),
        Regex("""\b(\d{1,2}(?:[.,]\d)?)\s*(?:büyüklüğünde|büyüklükte|şiddetinde)\b""")
    )
    return patterns.asSequence()
        .mapNotNull{it.find(lower)?.groupValues?.getOrNull(1)?.replace(',','.')?.toDoubleOrNull()}
        .firstOrNull{it in 1.0..10.0}
}

fun similarity(a:String,b:String):Double{
    val x=tokens(a)
    val y=tokens(b)
    return jaccard(x,y)*0.55 + overlap(x,y)*0.45
}

fun isSameEvent(item:FetchedItem,event:EventEntity):Boolean{
    val a=tokens(item.title+" "+item.summary.take(180))
    val b=tokens(event.title+" "+event.summary.take(180))
    val common=a.intersect(b).size
    val score=jaccard(a,b)*0.55+overlap(a,b)*0.45

    val aEq=(item.title+" "+item.summary).contains("deprem",true)
    val bEq=(event.title+" "+event.summary).contains("deprem",true)

    if(aEq||bEq){
        if(!(aEq&&bEq))return false
        val ma=earthquakeMagnitude(item.title+" "+item.summary)
        val mb=earthquakeMagnitude(event.title+" "+event.summary)
        if(ma!=null&&mb!=null&&abs(ma-mb)>0.35)return false
        return common>=2 && score>=0.48
    }

    return (common>=3 && score>=0.44) || (common>=4 && overlap(a,b)>=0.60)
}
