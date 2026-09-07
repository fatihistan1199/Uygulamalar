package tr.com.gundemradari.scan

import tr.com.gundemradari.data.EventEntity
import java.util.Locale
import kotlin.math.abs

private val clusterLocale=Locale("tr","TR")
private val stopWords=setOf(
    "ve","ile","için","ama","fakat","ancak","bir","bu","şu","da","de","mi","mı","mu","mü",
    "son","dakika","yeni","haber","haberi","açıkladı","dedi","oldu","oluyor","olacak",
    "sonrası","üzerine","göre","karşı","hakkında","ardından","nedeniyle","nedeni","bugün","dün"
)

private fun tokens(text:String):Set<String> = sanitizeNewsText(text).lowercase(clusterLocale)
    .split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>2 && it !in stopWords}.toSet()

private fun titleTokens(text:String)=tokens(text).filter{it.length>=4}.toSet()
private fun jaccard(a:Set<String>,b:Set<String>)=if(a.isEmpty()||b.isEmpty())0.0 else a.intersect(b).size.toDouble()/a.union(b).size
private fun overlap(a:Set<String>,b:Set<String>)=if(a.isEmpty()||b.isEmpty())0.0 else a.intersect(b).size.toDouble()/minOf(a.size,b.size)

private fun eventKind(text:String):String{
    val t=text.lowercase(clusterLocale)
    return when{
        t.contains("deprem")->"earthquake"
        t.contains("yanardağ")||t.contains("volkan")->"volcano"
        t.contains("seçim")||t.contains("referandum")->"election"
        t.contains("yangın")->"fire"
        t.contains("sel")||t.contains("heyelan")->"flood"
        t.contains("saldırı")||t.contains("füze")||t.contains("çatışma")->"attack"
        t.contains("faiz")||t.contains("enflasyon")->"economy"
        else->"general"
    }
}

private fun earthquakeMagnitude(text:String):Double?{
    val lower=text.lowercase(clusterLocale)
    val patterns=listOf(
        Regex("""\b(?:mw|ml|mb|m)\s*[:=]?\s*(\d{1,2}(?:[.,]\d)?)\b"""),
        Regex("""\b(\d{1,2}(?:[.,]\d)?)\s*(?:büyüklüğünde|büyüklükte|şiddetinde)\b""")
    )
    return patterns.asSequence().mapNotNull{it.find(lower)?.groupValues?.getOrNull(1)?.replace(',','.')?.toDoubleOrNull()}.firstOrNull{it in 1.0..10.0}
}

fun similarity(a:String,b:String):Double{
    val x=tokens(a); val y=tokens(b)
    return jaccard(x,y)*0.45+overlap(x,y)*0.55
}

fun isSameEvent(item:FetchedItem,event:EventEntity):Boolean{
    val itemText=item.title+" "+item.summary.take(260)
    val eventText=event.title+" "+event.summary.take(260)
    val a=tokens(itemText); val b=tokens(eventText)
    val at=titleTokens(item.title); val bt=titleTokens(event.title)
    val kindA=eventKind(itemText); val kindB=eventKind(eventText)
    if(kindA!="general"&&kindB!="general"&&kindA!=kindB)return false

    val common=a.intersect(b).size
    val titleCommon=at.intersect(bt).size
    val score=jaccard(a,b)*0.40+overlap(a,b)*0.35+overlap(at,bt)*0.25

    if(kindA=="earthquake"||kindB=="earthquake"){
        if(!(kindA=="earthquake"&&kindB=="earthquake"))return false
        val ma=earthquakeMagnitude(itemText); val mb=earthquakeMagnitude(eventText)
        if(ma!=null&&mb!=null&&abs(ma-mb)>0.35)return false
        return common>=2 && (score>=0.42 || titleCommon>=2)
    }
    if(kindA!="general"&&kindA==kindB)return common>=2 && (score>=0.40 || titleCommon>=2)

    return (common>=3&&score>=0.43)||(titleCommon>=3&&overlap(at,bt)>=0.55)||(common>=4&&overlap(a,b)>=0.58)
}
