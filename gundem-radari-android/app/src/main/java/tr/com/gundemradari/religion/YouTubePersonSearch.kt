package tr.com.gundemradari.religion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class YouTubePersonResult(
    val title:String,
    val url:String
)

class YouTubePersonSearch {
    suspend fun search(person:String,limit:Int=5):List<YouTubePersonResult> =
        withContext(Dispatchers.IO){
            runCatching{
                val q=URLEncoder.encode(person,StandardCharsets.UTF_8.toString())
                val html=Jsoup.connect("https://www.youtube.com/results?search_query=$q")
                    .userAgent("Mozilla/5.0 (Android) GundemRadari/0.11")
                    .timeout(12000)
                    .get()
                    .html()

                val regex=Regex(
                    """\"videoId\":\"([A-Za-z0-9_-]{11})\"[\s\S]{0,1800}?\"title\":\{\"runs\":\[\{\"text\":\"((?:\\.|[^\"\\])*)\"""",
                    RegexOption.IGNORE_CASE
                )

                regex.findAll(html)
                    .mapNotNull{m->
                        val id=m.groupValues.getOrNull(1).orEmpty()
                        val raw=m.groupValues.getOrNull(2).orEmpty()
                        val title=decodeJsonText(raw)
                        if(id.isBlank()||title.isBlank())null
                        else YouTubePersonResult(
                            title=title,
                            url="https://www.youtube.com/watch?v=$id"
                        )
                    }
                    .filter{ReligionWatchEngine.matchedPerson(it.title,"")!=null}
                    .distinctBy{it.url}
                    .take(limit)
                    .toList()
            }.getOrElse{emptyList()}
        }

    private fun decodeJsonText(raw:String):String =
        raw.replace("\\u0026","&")
            .replace("\\u003d","=")
            .replace("\\u0027","'")
            .replace("\\\"","\"")
            .replace("\\n"," ")
            .replace("\\/","/")
            .trim()
}
