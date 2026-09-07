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
    "deprem mi oldu","az önce deprem","son dakika deprem mi oldu",
    "kaç büyüklüğünde deprem","deprem nerede oldu","deprem oldu mu",
    "hangi illerde hissedildi","son depremler","kandilli son depremler"
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
    "tbmm kabul etti"
)

private val massCasualtyWords=listOf(
    "toplu ölüm","çok sayıda ölü","çok sayıda kişi hayatını kaybetti",
    "can kaybı","hayatını kaybetti","yaşamını yitirdi",
    "öldü","ölü","cenazesi","enkaz altında","kayıp"
)

private val severeDamageWords=listOf(
    "yıkıldı","yıkılan bina","ağır hasar","ciddi hasar","enkaz",
    "çöktü","çökme","tahliye edildi","afet bölgesi"
)

private val majorConflict=listOf(
    "savaş ilan","savaş başladı","askeri harekat","askerî harekât",
    "işgal","füze saldırısı","hava saldırısı","toplu saldırı",
    "sınır ötesi operasyon","terör saldırısı"
)

private val disasterWords=listOf(
    "sel felaketi","orman yangını","heyelan","çığ","tsunami",
    "kasırga","hortum","afet","baraj yıkıldı","baraj çöktü"
)

private fun normalized(title:String,summary:String):String =
    (" $title $summary ").lowercase(locale)
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

    val isEarthquake=t.contains("deprem")||t.contains("earthquake")
    if(isEarthquake){
        val magnitude=extractEarthquakeMagnitude(t)
        val deaths=extractDeathCount(t)
        val deadly=deaths!=null&&deaths>0 || massCasualtyWords.any{t.contains(it)}
        val destructive=severeDamageWords.any{t.contains(it)}

        importance+=when{
            magnitude!=null&&magnitude>7.0->68.0
            magnitude!=null&&magnitude>6.0->54.0
            magnitude!=null&&magnitude>=5.5&&deadly->50.0
            deadly||destructive->48.0
            magnitude!=null&&magnitude>=5.0->18.0
            else->4.0
        }

        if(magnitude!=null&&magnitude<=4.9&&!deadly&&!destructive)noise+=28.0
        if(trivialEarthquake.any{t.contains(it)}&&!deadly&&!destructive&&(magnitude==null||magnitude<=6.0)){
            noise+=52.0
            importance-=18.0
        }
    }

    if(nationwideCritical.any{t.contains(it)})importance=max(importance,88.0)

    if(majorStateActions.any{t.contains(it)}){
        importance+=28.0
        if(t.contains("türkiye")||t.contains("tbmm")||t.contains("tcmb")||t.contains("cumhurbaşkanı")){
            importance+=10.0
        }
    }

    if(majorConflict.any{t.contains(it)})importance+=36.0

    if(disasterWords.any{t.contains(it)}){
        importance+=24.0
        val deaths=extractDeathCount(t)
        if(deaths!=null&&deaths>=5)importance+=24.0
        if(massCasualtyWords.any{t.contains(it)})importance+=16.0
    }

    val deaths=extractDeathCount(t)
    if(deaths!=null){
        importance+=when{
            deaths>=100->42.0
            deaths>=20->32.0
            deaths>=5->22.0
            deaths>=1->10.0
            else->0.0
        }
    }else if(
        t.contains("toplu ölüm")||
        t.contains("çok sayıda ölü")||
        t.contains("çok sayıda kişi hayatını kaybetti")
    ){
        importance+=30.0
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

    if(source.groupName=="official")importance+=5.0

    importance+=min(8.0,max(0,sourceCount-1)*2.0)
    importance-=noise*0.45

    return min(100.0,max(0.0,importance)) to min(100.0,max(0.0,noise))
}
