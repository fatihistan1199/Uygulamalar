package tr.com.gundemradari.scan

import tr.com.gundemradari.data.EventEntity
import java.util.Locale
import kotlin.math.abs

private val clusterLocale=Locale("tr","TR")

private val stopWords=setOf(
    "ve","ile","için","ama","fakat","ancak","bir","bu","şu","da","de","mi","mı","mu","mü",
    "son","dakika","yeni","haber","haberi","açıkladı","dedi","oldu","oluyor","olacak",
    "sonrası","üzerine","göre","karşı","hakkında","ardından","nedeniyle","nedeni",
    "bugün","dün","video","youtube"
)

private fun canonicalToken(raw:String):String{
    var token=raw
    val suffixes=listOf(
        "sında","sinde","sunda","sünde",
        "ında","inde","unda","ünde",
        "daki","deki","taki","teki",
        "ını","ini","unu","ünü",
        "dan","den","tan","ten",
        "da","de","ta","te"
    )

    for(suffix in suffixes){
        if(
            token.endsWith(suffix) &&
            token.length-suffix.length>=4
        ){
            token=token.dropLast(suffix.length)
            break
        }
    }

    return token
}

private fun tokens(text:String):Set<String> =
    sanitizeNewsText(text)
        .lowercase(clusterLocale)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .map(::canonicalToken)
        .filter{it.length>2 && it !in stopWords}
        .toSet()

private fun titleTokens(text:String)=tokens(text).filter{it.length>=4}.toSet()


private val placeAnchors=listOf(
    "adana","adıyaman","afyonkarahisar","ağrı","amasya","ankara","antalya","artvin",
    "aydın","balıkesir","bilecik","bingöl","bitlis","bolu","burdur","bursa","çanakkale",
    "çankırı","çorum","denizli","diyarbakır","edirne","elazığ","erzincan","erzurum",
    "eskişehir","gaziantep","giresun","gümüşhane","hakkari","hatay","isparta","mersin",
    "istanbul","izmir","kars","kastamonu","kayseri","kırklareli","kırşehir","kocaeli",
    "konya","kütahya","malatya","manisa","kahramanmaraş","mardin","muğla","muş",
    "nevşehir","niğde","ordu","rize","sakarya","samsun","siirt","sinop","sivas",
    "tekirdağ","tokat","trabzon","tunceli","şanlıurfa","uşak","van","yozgat","zonguldak",
    "aksaray","bayburt","karaman","kırıkkale","batman","şırnak","bartın","ardahan",
    "iğdır","yalova","karabük","kilis","osmaniye","düzce",
    "girne","lefkoşa","gazimağusa","kktc",
    "gazze","kudüs","tel aviv","tahran","bağdat","şam","beyrut","moskova","kiev",
    "washington","new york","londra","paris","berlin","roma","atina","bali","jakarta",
    "endonezya","iran","irak","suriye","israil","filistin","rusya","ukrayna","abd",
    "almanya","fransa","italya","yunanistan","çin","japonya","hindistan","pakistan"
)

private fun extractPlaces(text:String):Set<String>{
    val lower=" "+sanitizeNewsText(text).lowercase(clusterLocale)+" "
    return placeAnchors.filter{place->
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(place)}(?![\\p{L}\\p{N}])")
            .containsMatchIn(lower)
    }.toSet()
}

private fun conflictingPlaces(
    aText:String,
    bText:String,
    kindA:String,
    kindB:String
):Boolean{
    if(kindA=="general" || kindB=="general")return false
    val aPlaces=extractPlaces(aText)
    val bPlaces=extractPlaces(bText)
    if(aPlaces.isEmpty()||bPlaces.isEmpty())return false
    return aPlaces.intersect(bPlaces).isEmpty()
}

private fun jaccard(a:Set<String>,b:Set<String>):Double =
    if(a.isEmpty()||b.isEmpty())0.0
    else a.intersect(b).size.toDouble()/a.union(b).size

private fun overlap(a:Set<String>,b:Set<String>):Double =
    if(a.isEmpty()||b.isEmpty())0.0
    else a.intersect(b).size.toDouble()/minOf(a.size,b.size)

private fun eventKind(text:String):String{
    val ts=tokens(text)

    fun hasStem(vararg stems:String):Boolean =
        ts.any{token->
            stems.any{stem->token.startsWith(stem)}
        }

    val waterDisaster=
        ts.contains("sel") ||
        hasStem("heyelan")

    val transport=
        (
            hasStem("gemi","tekne","feribot") &&
            hasStem("kaza","bat")
        ) || (
            hasStem("uçak","helikopter","tren") &&
            hasStem("kaza","düş")
        )

    return when{
        hasStem("deprem")->"earthquake"
        hasStem("yanardağ","volkan")->"volcano"
        hasStem("seçim","referandum")->"election"
        hasStem("yangın")->"fire"
        waterDisaster->"flood"
        hasStem("saldır","füze","çatış")->"attack"
        hasStem("faiz","enflasyon")->"economy"
        transport->"transport"
        else->"general"
    }
}

private fun earthquakeMagnitude(text:String):Double?{
    val lower=text.lowercase(clusterLocale)
    val patterns=listOf(
        Regex("""\b(?:mw|ml|mb|m)\s*[:=]?\s*(\d{1,2}(?:[.,]\d)?)\b"""),
        Regex("""\b(\d{1,2}(?:[.,]\d)?)\s*(?:büyüklüğünde|büyüklükte|şiddetinde)\b""")
    )
    return patterns.asSequence()
        .mapNotNull{
            it.find(lower)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(',','.')
                ?.toDoubleOrNull()
        }
        .firstOrNull{it in 1.0..10.0}
}

fun similarity(a:String,b:String):Double{
    val x=tokens(a)
    val y=tokens(b)
    return jaccard(x,y)*0.42+overlap(x,y)*0.58
}

fun isSameEvent(item:FetchedItem,event:EventEntity):Boolean{
    val itemText=item.title+" "+item.summary.take(300)
    val eventText=event.title+" "+event.summary.take(300)

    val a=tokens(itemText)
    val b=tokens(eventText)
    val at=titleTokens(item.title)
    val bt=titleTokens(event.title)

    val kindA=eventKind(itemText)
    val kindB=eventKind(eventText)

    if(kindA!="general"&&kindB!="general"&&kindA!=kindB){
        return false
    }

    if(conflictingPlaces(itemText,eventText,kindA,kindB)){
        return false
    }

    val itemTime=item.publishedAt
    val eventTime=event.publishedAt?:event.updatedAt
    if(itemTime!=null){
        val gap=abs(itemTime-eventTime)
        val maxGap=when{
            kindA=="general"&&kindB=="general"->5L*24*3600*1000
            else->7L*24*3600*1000
        }
        if(gap>maxGap)return false
    }

    val common=a.intersect(b).size
    val titleCommon=at.intersect(bt).size
    val bodyScore=jaccard(a,b)*0.38+overlap(a,b)*0.34
    val titleScore=overlap(at,bt)
    val score=bodyScore+titleScore*0.28

    if(kindA=="earthquake"||kindB=="earthquake"){
        if(!(kindA=="earthquake"&&kindB=="earthquake"))return false

        val ma=earthquakeMagnitude(itemText)
        val mb=earthquakeMagnitude(eventText)

        if(ma!=null&&mb!=null&&abs(ma-mb)>0.35)return false

        return common>=2 && (
            score>=0.40 ||
            titleCommon>=2
        )
    }

    if(kindA!="general"&&kindA==kindB){
        return common>=2 && (
            score>=0.37 ||
            titleCommon>=2
        )
    }

    return (
        common>=3 && score>=0.40
    ) || (
        titleCommon>=3 && titleScore>=0.52
    ) || (
        common>=4 && overlap(a,b)>=0.55
    ) || (
        titleCommon>=2 &&
        common>=3 &&
        titleScore>=0.46 &&
        score>=0.35
    )
}


fun likelySameSearchEvent(a:EventEntity,b:EventEntity):Boolean{
    val aText=a.title+" "+a.summary.take(320)
    val bText=b.title+" "+b.summary.take(320)

    val kindA=eventKind(aText)
    val kindB=eventKind(bText)

    if(kindA!="general"&&kindB!="general"&&kindA!=kindB){
        return false
    }

    if(conflictingPlaces(aText,bText,kindA,kindB)){
        return false
    }

    val aTime=a.publishedAt?:a.updatedAt
    val bTime=b.publishedAt?:b.updatedAt
    val gap=abs(aTime-bTime)

    val maxGap=if(
        kindA=="general"&&kindB=="general"
    ){
        3L*24*3600*1000
    }else{
        7L*24*3600*1000
    }

    if(gap>maxGap)return false

    if(kindA=="earthquake"&&kindB=="earthquake"){
        val ma=earthquakeMagnitude(aText)
        val mb=earthquakeMagnitude(bText)

        if(ma!=null&&mb!=null&&abs(ma-mb)>0.35){
            return false
        }
    }

    if(kindA=="transport"&&kindB=="transport"){
        val shared=tokens(aText).intersect(tokens(bText))
        if(shared.size>=4){
            return true
        }
    }

    val aTitle=titleTokens(a.title)
    val bTitle=titleTokens(b.title)
    val titleCommon=aTitle.intersect(bTitle).size
    val titleOverlap=overlap(aTitle,bTitle)
    val overall=similarity(aText,bText)

    return if(kindA!="general"&&kindA==kindB){
        (
            titleCommon>=2 &&
            (
                titleOverlap>=0.34 ||
                overall>=0.30
            )
        ) || (
            titleCommon>=1 &&
            overall>=0.40
        )
    }else{
        (
            titleCommon>=3 &&
            titleOverlap>=0.48
        ) || (
            titleCommon>=2 &&
            overall>=0.48
        ) || (
            overall>=0.60
        )
    }
}
