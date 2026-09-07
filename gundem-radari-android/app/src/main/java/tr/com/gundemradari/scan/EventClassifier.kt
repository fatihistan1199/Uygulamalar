package tr.com.gundemradari.scan

import tr.com.gundemradari.data.ClassificationItemRow
import tr.com.gundemradari.data.EventEntity
import java.util.Locale

private val trLocale=Locale("tr","TR")

data class ClassificationDecision(
    val scope:String,
    val turkeyScore:Int,
    val worldScore:Int,
    val reason:String
)

object EventClassifier {
    private val turkeyInstitutions=listOf(
        "tbmm","tcmb","ysk","afad","anayasa mahkemesi","yargıtay",
        "cumhurbaşkanlığı","cumhurbaşkanı erdoğan","recep tayyip erdoğan",
        "hazine ve maliye bakanlığı","içişleri bakanlığı","milli eğitim bakanlığı",
        "millî eğitim bakanlığı","sağlık bakanlığı","adalet bakanlığı",
        "dışişleri bakanlığı","türk silahlı kuvvetleri","tsk","türkiye büyük millet meclisi"
    )

    private val turkeyExplicit=listOf(
        "türkiye'de","türkiye’de","türkiye genelinde","ülke genelinde","81 il",
        "türkiye'nin","türkiye’nin","türkiye'ye","türkiye’ye","türkiye'den","türkiye’den",
        "türk vatandaş","türk lirası","türk ekonomisi"
    )

    private val turkeyProvinces=listOf(
        "adana","adıyaman","afyonkarahisar","ağrı","amasya","ankara","antalya","artvin",
        "aydın","balıkesir","bilecik","bingöl","bitlis","bolu","burdur","bursa","çanakkale",
        "çankırı","çorum","denizli","diyarbakır","edirne","elazığ","erzincan","erzurum",
        "eskişehir","gaziantep","giresun","gümüşhane","hakkari","hatay","isparta","mersin",
        "istanbul","izmir","kars","kastamonu","kayseri","kırklareli","kırşehir","kocaeli",
        "konya","kütahya","malatya","manisa","kahramanmaraş","mardin","muğla","muş",
        "nevşehir","niğde","ordu","rize","sakarya","samsun","siirt","sinop","sivas",
        "tekirdağ","tokat","trabzon","tunceli","şanlıurfa","uşak","van","yozgat","zonguldak",
        "aksaray","bayburt","karaman","kırıkkale","batman","şırnak","bartın","ardahan",
        "iğdır","yalova","karabük","kilis","osmaniye","düzce"
    )

    private val foreignPlaces=listOf(
        "endonezya","bali","ceakarta","jakarta","java","sumatra","filipinler","manila",
        "malezya","singapur","tayland","bangkok","vietnam","hanoi","çin","pekin","şanghay",
        "japonya","tokyo","güney kore","seul","kuzey kore","hindistan","yeni delhi",
        "pakistan","islamabad","bangladeş","nepal","katmandu","afganistan","kabil",
        "iran","tahran","irak","bağdat","suriye","şam","lübnan","beyrut","israil",
        "tel aviv","kudüs","gazze","filistin","ürdün","amman","suudi arabistan","riyad",
        "yemen","sana","katar","doha","birleşik arap emirlikleri","dubai","mısır","kahire",
        "libya","trablus","sudan","hartum","tunus","cezayir","fas","rabat",
        "yunanistan","atina","bulgaristan","sofya","romanya","bükreş","sırbistan","belgrad",
        "bosna","kosova","arnavutluk","kuzey makedonya","gürcistan","tiflis","ermenistan",
        "erivan","azerbaycan","bakü","rusya","moskova","ukrayna","kiev","belarus",
        "polonya","varşova","almanya","berlin","fransa","paris","italya","roma","ispanya",
        "madrid","portekiz","lizbon","ingiltere","londra","birleşik krallık","irlanda",
        "hollanda","amsterdam","belçika","brüksel","avusturya","viyana","isviçre","bern",
        "isveç","stokholm","norveç","oslo","finlandiya","helsinki","danimarka","kopenhag",
        "amerika birleşik devletleri","abd","washington","new york","kanada","ottawa",
        "meksika","meksiko","brezilya","brasilia","arjantin","buenos aires","şili",
        "kolombiya","venezuela","küba","avustralya","sidney","melbourne","yeni zelanda",
        "güney afrika","nijerya","kenya","etiyopya","somali","mozambik","kongo"
    )

    private val worldInstitutions=listOf(
        "nato","avrupa birliği","ab komisyonu","birleşmiş milletler","bm güvenlik konseyi",
        "beyaz saray","pentagon","kremlin","avrupa parlamentosu"
    )

    private fun normalize(text:String)=text.lowercase(trLocale)
        .replace('’','\'')
        .replace(Regex("\\s+")," ")

    private fun has(text:String,term:String):Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term.lowercase(trLocale))}(?![\\p{L}\\p{N}])")
            .containsMatchIn(text)

    fun classify(event:EventEntity,items:List<ClassificationItemRow>):ClassificationDecision{
        if(event.religionPriority>0){
            return ClassificationDecision("religion",0,0,"din takip listesi")
        }

        val combined=normalize(buildString{
            append(event.title).append(' ').append(event.summary)
            items.forEach{append(' ').append(it.title).append(' ').append(it.summary)}
        })

        var turkeyScore=0
        var worldScore=0

        turkeyInstitutions.forEach{if(has(combined,it))turkeyScore+=4}
        turkeyExplicit.forEach{if(has(combined,it))turkeyScore+=4}
        turkeyProvinces.forEach{if(has(combined,it))turkeyScore+=2}
        foreignPlaces.forEach{if(has(combined,it))worldScore+=3}
        worldInstitutions.forEach{if(has(combined,it))worldScore+=2}

        val sourceGroups=items.map{it.groupName}
        if(sourceGroups.any{it=="world_tr"})worldScore+=1
        if(sourceGroups.any{it=="turkey"})turkeyScore+=1

        val explicitForeign=foreignPlaces.any{has(combined,it)}
        val explicitTurkey=turkeyExplicit.any{has(combined,it)} ||
            turkeyInstitutions.any{has(combined,it)}

        val scope=when{
            explicitForeign && !explicitTurkey -> "world"
            explicitForeign && turkeyScore<worldScore+2 -> "world"
            explicitTurkey && turkeyScore>=worldScore -> "turkey"
            worldScore>turkeyScore -> "world"
            turkeyScore>=3 -> "turkey"
            sourceGroups.count{it=="world_tr"}>sourceGroups.count{it=="turkey"} -> "world"
            sourceGroups.any{it=="turkey"} -> "turkey"
            else -> "world"
        }

        return ClassificationDecision(
            scope=scope,
            turkeyScore=turkeyScore,
            worldScore=worldScore,
            reason=if(scope=="turkey")"Türkiye coğrafyası/kurumu" else "yabancı coğrafya/dünya bağlamı"
        )
    }
}
