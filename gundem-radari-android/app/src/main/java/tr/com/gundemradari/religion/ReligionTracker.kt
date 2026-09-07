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
        "canlı yayın","röportaj","sohbet"
    )

    private val religionTopicTerms=listOf(
        "diyanet","hutbe","kur'an","kuran","hadis","tefsir","islam","islâm","müslüman",
        "ramazan","hac","umre","fetva","ilahiyat","imam","müftü","vaaz","namaz","oruç",
        "zekat","zekât","tasavvuf","tarikat","cemaat","akaid","kelam","fıkıh","sünnet"
    )

    private fun normalize(text:String)=text.lowercase(Locale("tr","TR"))

    fun priority(source:SourceEntity,title:String,summary:String):Int{
        val text=normalize("$title $summary")
        val followed=aliases.any{text.contains(it)}
        val religionTopic=religionTopicTerms.any{text.contains(it)}

        if(source.groupName=="religion_direct" && religionTopic) return 3
        if(!followed) return 0

        val named=directPeople.any{text.contains(it)}
        val looksDirect=title.contains(":") || title.contains("\"") ||
            speechMarkers.any{normalize(title).contains(it)}
        return if(named && looksDirect) 2 else 1
    }
}
