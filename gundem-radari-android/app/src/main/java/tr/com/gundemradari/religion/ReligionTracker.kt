package tr.com.gundemradari.religion

import tr.com.gundemradari.data.SourceEntity
import java.util.Locale

object ReligionTracker {
    val displayFollowList=listOf(
        "Mehmet Okuyan",
        "Enis Doko",
        "Diyanet",
        "Cemaatler",
        "Tarikatler",
        "Cübbeli Ahmet (Ahmet Mahmut Ünlü)",
        "İsrafil Balcı",
        "Altay Cem Meriç"
    )

    val searchQueries=listOf(
        "\"Mehmet Okuyan\"",
        "\"Enis Doko\"",
        "\"Diyanet\"",
        "cemaatler",
        "tarikatler OR tarikatlar",
        "\"Cübbeli Ahmet\" OR \"Ahmet Mahmut Ünlü\"",
        "\"İsrafil Balcı\"",
        "\"Altay Cem Meriç\""
    )

    private val aliases=listOf(
        "mehmet okuyan",
        "enis doko",
        "diyanet",
        "diyanet işleri",
        "cemaat",
        "cemaatler",
        "dini cemaat",
        "tarikat",
        "tarikatlar",
        "tarikatler",
        "cübbeli ahmet",
        "ahmet mahmut ünlü",
        "israfil balcı",
        "israfil balci",
        "altay cem meriç",
        "altay cem meric"
    )

    private val directPeople=listOf(
        "mehmet okuyan",
        "enis doko",
        "diyanet",
        "diyanet işleri",
        "cübbeli ahmet",
        "ahmet mahmut ünlü",
        "israfil balcı",
        "israfil balci",
        "altay cem meriç",
        "altay cem meric"
    )

    private val speechMarkers=listOf(
        "açıkladı","açıklaması","dedi","söyledi","değerlendirdi","anlattı","yanıtladı",
        "cevapladı","duyurdu","paylaştı","konuştu","yazdı","uyardı","çağrı yaptı","ifade etti",
        "canlı yayın","röportaj","sohbet","hutbe","duyuru"
    )

    private fun normalize(text:String)=text.lowercase(Locale("tr","TR"))

    fun matches(title:String,summary:String):Boolean{
        val text=normalize("$title $summary")
        return aliases.any{text.contains(it)}
    }

    fun priority(source:SourceEntity,title:String,summary:String):Int{
        val normalizedTitle=normalize(title)
        val text=normalize("$title $summary")

        if(source.id=="diyanet_haber"){
            val direct=speechMarkers.any{normalizedTitle.contains(it)} ||
                normalizedTitle.contains("başkan") ||
                normalizedTitle.contains("diyanet işleri")
            return if(direct)3 else 1
        }

        val followed=aliases.any{text.contains(it)}
        if(!followed)return 0

        val named=directPeople.any{text.contains(it)}
        val looksDirect=title.contains(":") || title.contains("\"") ||
            speechMarkers.any{normalizedTitle.contains(it)}
        return if(named && looksDirect)3 else 1
    }
}
