package tr.com.gundemradari.religion

import tr.com.gundemradari.data.SourceEntity
import java.util.Locale

const val RELIGION_MAX_AGE_DAYS=30
const val RELIGION_MAX_AGE_MS=RELIGION_MAX_AGE_DAYS*24L*60*60*1000

data class ReligionPersonProfile(
    val name:String,
    val aliases:List<String>,
    val websiteUrl:String="",
    val youtubeUrl:String="",
    val xUrl:String="",
    val instagramUrl:String="",
    val facebookUrl:String=""
){
    fun links():List<Pair<String,String>> = buildList{
        if(websiteUrl.isNotBlank())add("Web sitesi" to websiteUrl)
        if(youtubeUrl.isNotBlank())add("YouTube" to youtubeUrl)
        if(xUrl.isNotBlank())add("X" to xUrl)
        if(instagramUrl.isNotBlank())add("Instagram" to instagramUrl)
        if(facebookUrl.isNotBlank())add("Facebook" to facebookUrl)
    }
}

object ReligionTracker {
    val profiles=listOf(
        ReligionPersonProfile(
            name="Mehmet Okuyan",
            aliases=listOf("mehmet okuyan"),
            websiteUrl="https://www.mehmetokuyan.com",
            youtubeUrl="https://www.youtube.com/@okuyanmehmet",
            xUrl="https://x.com/okuyanmehmet",
            instagramUrl="https://www.instagram.com/okuyanmehmet/",
            facebookUrl="https://www.facebook.com/okuyanmehmet"
        ),
        ReligionPersonProfile(
            name="Enis Doko",
            aliases=listOf("enis doko"),
            websiteUrl="https://www.enisdoko.com",
            xUrl="https://x.com/enis_doko",
            instagramUrl="https://www.instagram.com/enisdoko/",
            facebookUrl="https://www.facebook.com/enis.doko"
        ),
        ReligionPersonProfile(
            name="Cübbeli Ahmet",
            aliases=listOf("cübbeli ahmet","ahmet mahmut ünlü"),
            websiteUrl="https://www.cubbeliahmethoca.com.tr",
            youtubeUrl="https://www.youtube.com/cubbeliahmethoca",
            xUrl="https://x.com/c_ahmethoca",
            instagramUrl="https://www.instagram.com/cubbeliahmethoca/",
            facebookUrl="https://www.facebook.com/cubbeliahmethoca"
        ),
        ReligionPersonProfile(
            name="İsrafil Balcı",
            aliases=listOf("israfil balcı","israfil balci"),
            websiteUrl="https://israfilbalci.com/",
            youtubeUrl="https://www.youtube.com/@israfilbalcı"
        ),
        ReligionPersonProfile(
            name="Altay Cem Meriç",
            aliases=listOf("altay cem meriç","altay cem meric"),
            youtubeUrl="https://www.youtube.com/c/AltayCemMeric",
            xUrl="https://x.com/AltayCemMeric",
            instagramUrl="https://www.instagram.com/dr.altaycemmeric23.10/",
            facebookUrl="https://www.facebook.com/altay.meric"
        )
    )

    val personQueries=profiles.map{it.name}

    // Cübbeli Ahmet gibi birden fazla yaygın adlandırması olan kişiler için
    // ikinci ad da bağımsız aranır; sonuçlar URL bazında tekilleştirilir.
    val searchQueries=profiles
        .flatMap{profile->
            buildList{
                add(profile.name)
                profile.aliases
                    .filterNot{it.equals(profile.name,true)}
                    .forEach{add(it)}
            }
        }
        .distinct()

    fun cutoff(now:Long=System.currentTimeMillis()):Long=
        now-RELIGION_MAX_AGE_MS

    fun isFresh(publishedAt:Long?,now:Long=System.currentTimeMillis()):Boolean=
        publishedAt!=null && publishedAt>=cutoff(now) && publishedAt<=now+24L*60*60*1000

    fun profileFor(title:String,summary:String=""):ReligionPersonProfile?{
        val normalized=normalize("$title $summary")
        return profiles.firstOrNull{profile->
            profile.aliases.any{alias->normalized.contains(normalize(alias))}
        }
    }

    private fun normalize(text:String)=text.lowercase(Locale("tr","TR"))
        .replace('’','\'')
        .replace(Regex("\\s+")," ")
        .trim()
}

data class ReligionMatch(
    val accepted:Boolean,
    val priority:Int,
    val person:String?=null
)

object ReligionWatchEngine {
    private val locale=Locale("tr","TR")

    private val directSpeech=listOf(
        "dedi","söyledi","açıkladı","değerlendirdi","yanıtladı","cevapladı",
        "konuştu","yazdı","uyardı","ifade etti","röportaj","canlı yayın","sohbet",
        "yayınında","videosunda","programında","paylaştı","duyurdu"
    )

    private fun normalize(text:String)=text.lowercase(locale)
        .replace('’','\'')
        .replace(Regex("\\s+")," ")

    fun matchedPerson(title:String,summary:String):String?=
        ReligionTracker.profileFor(title,summary)?.name

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
