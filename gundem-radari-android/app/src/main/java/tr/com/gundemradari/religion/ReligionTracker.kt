package tr.com.gundemradari.religion

import tr.com.gundemradari.data.SourceEntity
import java.util.Locale

object ReligionTracker {
    val searchQueries=listOf(
        "\"Mehmet Okuyan\"","\"Enis Doko\"","\"Diyanet\"","cemaatler",
        "tarikatler OR tarikatlar","\"Cübbeli Ahmet\" OR \"Ahmet Mahmut Ünlü\"",
        "\"İsrafil Balcı\"","\"Altay Cem Meriç\""
    )
}

data class ReligionMatch(val accepted:Boolean,val priority:Int,val reason:String="")

object ReligionWatchEngine {
    private val locale=Locale("tr","TR")
    private val namedAliases=listOf(
        "mehmet okuyan","enis doko","cübbeli ahmet","ahmet mahmut ünlü",
        "israfil balcı","israfil balci","altay cem meriç","altay cem meric"
    )
    private val genericAliases=listOf("cemaat","cemaatler","dini cemaat","tarikat","tarikatlar","tarikatler")
    private val diyanetAliases=listOf("diyanet","diyanet işleri","diyanet işleri başkanlığı","diyanet işleri başkanı","başkan erbaş","ali erbaş")
    private val routineNoise=listOf(
        "cuma hutbesi","haftanın hutbesi","hutbe konusu","namaz vakti","imsakiye","ezan vakti",
        "iftar vakti","sahur vakti","günün duası","günün ayeti","günün hadisi","kandil mesajı",
        "mevlid kandili","regaip kandili","miraç kandili","berat kandili","kadir gecesi",
        "bayram namazı","hac ibadeti nasıl","umre nasıl yapılır","oruç nasıl tutulur"
    )
    private val institutionalActions=listOf(
        "açıkladı","açıklama","karar","atama","görevden","göreve","yönetmelik","genelge",
        "rapor","bütçe","soruşturma","inceleme","fetva","protokol","dava","tartışma",
        "tepki","eleştiri","yasak","başkanlık duyurdu","başkan erbaş","diyanet işleri başkanı"
    )
    private val directSpeech=listOf(
        "dedi","söyledi","açıkladı","değerlendirdi","yanıtladı","cevapladı",
        "konuştu","yazdı","uyardı","ifade etti","röportaj","canlı yayın","sohbet"
    )
    private val genericNewsActions=listOf(
        "operasyon","soruşturma","dava","tutuklandı","gözaltı","lider","şeyh","yapılanma",
        "faaliyet","kapatıldı","yasaklandı","açıklama","tartışma","rapor","iddia","karar","mahkeme"
    )

    private fun normalize(text:String)=text.lowercase(locale).replace('’','\'').replace(Regex("\\s+")," ")

    fun evaluate(source:SourceEntity,title:String,summary:String):ReligionMatch{
        val titleN=normalize(title)
        val text=normalize("$title $summary")
        val named=namedAliases.firstOrNull{text.contains(it)}
        val generic=genericAliases.firstOrNull{text.contains(it)}
        val diyanet=diyanetAliases.any{text.contains(it)}
        val routine=routineNoise.any{text.contains(it)}
        val institutional=institutionalActions.any{text.contains(it)}
        val direct=directSpeech.any{titleN.contains(it)} || title.contains(":") || title.contains("\"")

        if(named!=null)return ReligionMatch(true,if(direct)3 else 2,named)

        if(generic!=null){
            if(routine)return ReligionMatch(false,0,"rutin dini içerik")
            val meaningful=genericNewsActions.any{text.contains(it)} || titleN.contains(generic)
            return if(meaningful)ReligionMatch(true,if(direct)2 else 1,generic)
            else ReligionMatch(false,0,"zayıf eşleşme")
        }

        if(source.id=="diyanet_haber" || diyanet){
            if(routine && !institutional)return ReligionMatch(false,0,"rutin Diyanet içeriği")
            if(institutional)return ReligionMatch(true,if(direct)3 else 2,"Diyanet kurumsal gelişme")
            return ReligionMatch(false,0,"Diyanet rutin/genel içerik")
        }

        return ReligionMatch(false,0)
    }
}
