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
        "Cübbeli Ahmet" to listOf("cübbeli ahmet","ahmet mahmut ünlü"),
        "İsrafil Balcı" to listOf("israfil balcı","israfil balci"),
        "Altay Cem Meriç" to listOf("altay cem meriç","altay cem meric")
    )

    private val directSpeech=listOf(
        "dedi","söyledi","açıkladı","değerlendirdi","yanıtladı","cevapladı",
        "konuştu","yazdı","uyardı","ifade etti","röportaj","canlı yayın","sohbet",
        "yayınında","videosunda","programında"
    )

    private fun normalize(text:String)=text.lowercase(locale)
        .replace('’','\'')
        .replace(Regex("\\s+")," ")

    fun matchedPerson(title:String,summary:String):String?{
        val text=normalize("$title $summary")
        return people.entries.firstOrNull{(_,aliases)->
            aliases.any{text.contains(it)}
        }?.key
    }

    fun evaluate(source:SourceEntity,title:String,summary:String):ReligionMatch{
        val person=matchedPerson(title,summary) ?: return ReligionMatch(false,0,null)
        val titleN=normalize(title)
        val direct=directSpeech.any{titleN.contains(it)} ||
            title.contains(":") ||
            title.contains("\"") ||
            source.kind=="religion_youtube"
        return ReligionMatch(true,if(direct)3 else 2,person)
    }
}
