package tr.com.gundemradari.religion

import tr.com.gundemradari.data.SourceEntity
import java.util.Locale

object ReligionTracker {
    val personQueries=listOf(
        "Mehmet Okuyan",
        "Enis Doko",
        "Cübbeli Ahmet",
        "Ahmet Mahmut Ünlü",
        "İsrafil Balcı",
        "Altay Cem Meriç"
    )

    val searchQueries=personQueries.map{"\"$it\""}
}

data class ReligionMatch(
    val accepted:Boolean,
    val priority:Int,
    val person:String?=null
)

object ReligionWatchEngine {
    private val locale=Locale("tr","TR")

    private val people=linkedMapOf(
        "Mehmet Okuyan" to listOf("mehmet okuyan"),
        "Enis Doko" to listOf("enis doko"),
        "Cübbeli Ahmet" to listOf(
            "cübbeli ahmet",
            "ahmet mahmut ünlü"
        ),
        "İsrafil Balcı" to listOf(
            "israfil balcı",
            "israfil balci"
        ),
        "Altay Cem Meriç" to listOf(
            "altay cem meriç",
            "altay cem meric"
        )
    )

    private val directSpeech=listOf(
        "dedi","söyledi","açıkladı","değerlendirdi",
        "yanıtladı","cevapladı","konuştu","yazdı",
        "uyardı","ifade etti","röportaj","canlı yayın",
        "sohbet","yayınında","videosunda","programında",
        "itiraz etti","eleştirdi","savundu","duyurdu"
    )

    private val relationMarkers=listOf(
        "hakkında","ile ilgili","konuya ilişkin","açıklamasında",
        "açıklamada","görüşünü","değerlendirmesinde",
        "katıldığı","programında","yayınında","videosunda",
        "soruyu yanıtladı","cevap verdi","itiraz","eleştiri",
        "savundu","uyardı","dedi","söyledi","açıkladı",
        "konuştu","yazdı","yanıtladı","cevapladı"
    )

    private fun normalize(text:String)=text
        .lowercase(locale)
        .replace('’','\'')
        .replace(Regex("\\s+")," ")

    private fun matchedPersonIn(text:String):String?{
        val normalized=normalize(text)
        return people.entries.firstOrNull{(_,aliases)->
            aliases.any{normalized.contains(it)}
        }?.key
    }

    fun matchedPerson(
        title:String,
        summary:String
    ):String? =
        matchedPersonIn(title)
            ?: matchedPersonIn(summary)

    fun evaluate(
        source:SourceEntity,
        title:String,
        summary:String
    ):ReligionMatch{
        val titlePerson=matchedPersonIn(title)
        val summaryPerson=matchedPersonIn(summary)
        val person=titlePerson ?: summaryPerson
            ?: return ReligionMatch(false,0,null)

        // YouTube sonuçlarında kişinin adı doğrudan video başlığında olmalı.
        if(
            source.kind=="religion_youtube" &&
            titlePerson==null
        ){
            return ReligionMatch(false,0,null)
        }

        val titleN=normalize(title)
        val summaryN=normalize(summary)
        val combined="$titleN $summaryN"

        // Başlıkta kişi açıkça geçiyorsa haber doğrudan ilgili kabul edilir.
        if(titlePerson!=null){
            val direct=
                directSpeech.any{combined.contains(it)} ||
                title.contains(":") ||
                title.contains("\"")

            return ReligionMatch(
                accepted=true,
                priority=if(direct)3 else 2,
                person=person
            )
        }

        // İsim yalnız özet/gövde tarafında geçiyorsa açık ilişki sinyali gerekir.
        val related=relationMarkers.any{
            combined.contains(it)
        }

        if(!related){
            return ReligionMatch(false,0,null)
        }

        val direct=directSpeech.any{
            combined.contains(it)
        }

        return ReligionMatch(
            accepted=true,
            priority=if(direct)3 else 1,
            person=person
        )
    }
}
