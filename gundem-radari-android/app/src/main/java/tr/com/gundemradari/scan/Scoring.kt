package tr.com.gundemradari.scan

import tr.com.gundemradari.data.SourceEntity
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val locale=Locale("tr","TR")

private val generalNoise=listOf(
    "magazin","influencer","ünlü","viral","görenler inanamadı","ikiye bölündü",
    "sosyal medya bunu konuşuyor","şoke eden","çok konuşulacak","olay oldu",
    "gündem oldu","bakın ne yaptı","kimdir","kaç yaşında","nereli"
)

private val trivialEarthquake=listOf(
    "deprem mi oldu","az önce deprem","son dakika deprem mi oldu","kaç büyüklüğünde deprem",
    "deprem nerede oldu","deprem oldu mu","hangi illerde hissedildi","son depremler",
    "kandilli son depremler"
)

private val nationwideCritical=listOf(
    "genel seçim","cumhurbaşkanlığı seçimi","milletvekili genel seçimi",
    "referandum","erken seçim","seçim sonuçları","oy sayımı",
    "ülke genelinde","türkiye genelinde","81 il","tüm türkiye",
    "olağanüstü hal","ohal","seferberlik","sokağa çıkma yasağı",
    "anayasa değişikliği","anayasa değişikliği teklifi"
)

private val majorStateActions=listOf(
    "merkez bankası faiz","tcmb faiz","politika faizi",
    "resmi gazete","resmî gazete","kanun yürürlüğe girdi",
    "anayasa mahkemesi","yüksek seçim kurulu","ysk",
    "cumhurbaşkanı kararı","bakanlar kurulu","meclis kabul etti",
    "tbmm kabul etti","kabine kararı","kabine toplantısı"
)

private val massCasualtyWords=listOf(
    "toplu ölüm","çok sayıda ölü","çok sayıda kişi hayatını kaybetti",
    "can kaybı","hayatını kaybetti","yaşamını yitirdi",
    "öldü","ölü","cenazesi","enkaz altında","kayıp"
)

private val severeDamageWords=listOf(
    "yıkıldı","yıkılan bina","ağır hasar","ciddi hasar","enkaz",
    "çöktü","çökme","tahliye edildi","tahliye","afet bölgesi",
    "yerleşim yerlerine ulaştı","evler tahliye","çok sayıda ev"
)

private val majorConflict=listOf(
    "savaş ilan","savaş başladı","askeri harekat","askerî harekât",
    "işgal","füze saldırısı","hava saldırısı","toplu saldırı",
    "sınır ötesi operasyon","terör saldırısı"
)

private val disasterWords=listOf(
    "sel felaketi","orman yangını","heyelan","çığ","tsunami",
    "kasırga","hortum","afet","baraj yıkıldı","baraj çöktü",
    "yanardağ","volkan"
)

private val transportDisasterWords=listOf(
    "gemi kazası","gemi battı","tekne battı","feribot battı",
    "uçak kazası","uçak düştü","helikopter düştü",
    "tren kazası","otobüs kazası","zincirleme kaza"
)

private val routineLowImpact=listOf(
    "uyum haftası","oryantasyon programı","etkinlik düzenlenecek",
    "panel düzenleyecek","ilk balığı","çok okunanlar","kimdir"
)

private fun normalized(title:String,summary:String):String =
    (" $title $summary ")
        .lowercase(locale)
        .replace('’','\'')
        .replace(Regex("\\s+")," ")

private fun extractEarthquakeMagnitude(text:String):Double?{
    val patterns=listOf(
        Regex("""\b(?:mw|ml|mb|m)\s*[:=]?\s*(\d{1,2}(?:[.,]\d)?)\b""",RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,2}(?:[.,]\d)?)\s*(?:büyüklüğünde|büyüklükte|şiddetinde)\b""",RegexOption.IGNORE_CASE)
    )
    return patterns.asSequence()
        .mapNotNull{it.find(text)?.groupValues?.getOrNull(1)}
        .mapNotNull{it.replace(',','.').toDoubleOrNull()}
        .firstOrNull{it in 1.0..10.0}
}

private fun extractDeathCount(text:String):Int?{
    val patterns=listOf(
        Regex("""\b(\d{1,4})\s+(?:kişi\s+)?(?:öldü|ölü|hayatını kaybetti|yaşamını yitirdi)\b"""),
        Regex("""\bölü sayısı\s+(\d{1,4})\b"""),
        Regex("""\bcan kaybı\s+(\d{1,4})\b""")
    )
    return patterns.asSequence()
        .mapNotNull{it.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()}
        .maxOrNull()
}

fun scores(
    title:String,
    source:SourceEntity,
    sourceCount:Int=1,
    summary:String=""
):Pair<Double,Double>{
    val t=normalized(title,summary)
    var importance=16.0
    var noise=0.0

    generalNoise.forEach{if(t.contains(it))noise+=18.0}
    routineLowImpact.forEach{if(t.contains(it))noise+=8.0}

    val isEarthquake=t.contains("deprem")||t.contains("earthquake")
    if(isEarthquake){
        val magnitude=extractEarthquakeMagnitude(t)
        val deaths=extractDeathCount(t)
        val deadly=(deaths?:0)>0 || massCasualtyWords.any{t.contains(it)}
        val destructive=severeDamageWords.any{t.contains(it)}

        importance+=when{
            magnitude!=null&&magnitude>7.0->70.0
            magnitude!=null&&magnitude>6.0->56.0
            magnitude!=null&&magnitude>=5.5&&deadly->52.0
            deadly||destructive->50.0
            magnitude!=null&&magnitude>=5.0->18.0
            else->4.0
        }

        if(magnitude!=null&&magnitude<=4.9&&!deadly&&!destructive)noise+=30.0
        if(
            trivialEarthquake.any{t.contains(it)} &&
            !deadly &&
            !destructive &&
            (magnitude==null||magnitude<=6.0)
        ){
            noise+=54.0
            importance-=20.0
        }
    }

    if(nationwideCritical.any{t.contains(it)}){
        importance=max(importance,90.0)
    }

    if(majorStateActions.any{t.contains(it)}){
        importance+=26.0
        if(
            t.contains("türkiye")||
            t.contains("tbmm")||
            t.contains("tcmb")||
            t.contains("cumhurbaşkanı")||
            t.contains("kabine")
        ){
            importance+=10.0
        }
    }

    if(majorConflict.any{t.contains(it)})importance+=38.0

    if(disasterWords.any{t.contains(it)}){
        importance+=24.0

        if(severeDamageWords.any{t.contains(it)}){
            importance+=24.0
        }

        val deaths=extractDeathCount(t)
        if(deaths!=null&&deaths>=5)importance+=18.0
        if(massCasualtyWords.any{t.contains(it)})importance+=12.0
    }

    if(transportDisasterWords.any{t.contains(it)}){
        importance+=24.0
    }

    val deaths=extractDeathCount(t)
    if(deaths!=null){
        importance+=when{
            deaths>=100->60.0
            deaths>=20->48.0
            deaths>=10->38.0
            deaths>=5->30.0
            deaths>=1->14.0
            else->0.0
        }
    }else if(
        t.contains("toplu ölüm")||
        t.contains("çok sayıda ölü")||
        t.contains("çok sayıda kişi hayatını kaybetti")
    ){
        importance+=34.0
    }

    if(t.contains("savaş")||t.contains("çatışma"))importance+=18.0
    if(t.contains("seçim")&&!t.contains("yerel seçim"))importance+=18.0

    if(
        t.contains("türkiye")||
        t.contains("ülke genelinde")||
        t.contains("türkiye genelinde")||
        t.contains("81 il")
    ){
        importance+=10.0
    }

    importance+=(sourceQuality(source)-0.5)*10.0
    importance+=min(8.0,max(0,sourceCount-1)*2.0)
    importance-=noise*0.45

    return min(100.0,max(0.0,importance)) to min(100.0,max(0.0,noise))
}
