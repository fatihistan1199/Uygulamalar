package tr.com.gundemradari.religion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.OffsetDateTime

data class YouTubePersonResult(
    val title:String,
    val url:String,
    val publishedAt:Long
)

class YouTubePersonSearch {

    suspend fun search(
        profile:ReligionPersonProfile,
        limit:Int=5
    ):List<YouTubePersonResult> = withContext(Dispatchers.IO){
        if(profile.youtubeUrl.isBlank())return@withContext emptyList()

        runCatching{
            val channelId=resolveChannelId(profile.youtubeUrl)
                ?:return@runCatching emptyList()

            val xml=Jsoup.connect(
                "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            )
                .userAgent("Mozilla/5.0 (Android) GundemRadari/18")
                .timeout(12000)
                .ignoreContentType(true)
                .execute()
                .body()

            val doc=Jsoup.parse(xml,"",Parser.xmlParser())
            doc.select("entry")
                .mapNotNull{entry->
                    val title=entry.selectFirst("title")?.text().orEmpty().trim()
                    val url=entry.selectFirst("link[rel=alternate]")
                        ?.attr("href")
                        .orEmpty()
                    val published=entry.selectFirst("published")?.text().orEmpty()
                    val publishedAt=runCatching{
                        OffsetDateTime.parse(published).toInstant().toEpochMilli()
                    }.getOrNull()

                    if(
                        title.isBlank() ||
                        url.isBlank() ||
                        !ReligionTracker.isFresh(publishedAt)
                    ){
                        null
                    }else{
                        YouTubePersonResult(
                            title=title,
                            url=url,
                            publishedAt=publishedAt!!
                        )
                    }
                }
                .filter{row->
                    // Resmî kanal doğrudan izlendiği için başlıkta kişinin adı
                    // bulunması zorunlu değildir.
                    row.title.isNotBlank()
                }
                .distinctBy{it.url}
                .take(limit)
        }.getOrElse{emptyList()}
    }

    private fun resolveChannelId(channelUrl:String):String?{
        val html=Jsoup.connect(channelUrl)
            .userAgent("Mozilla/5.0 (Android) GundemRadari/18")
            .timeout(12000)
            .followRedirects(true)
            .get()
            .html()

        val patterns=listOf(
            Regex("\"channelId\":\"(UC[A-Za-z0-9_-]{20,})\""),
            Regex("\"externalId\":\"(UC[A-Za-z0-9_-]{20,})\""),
            Regex("channel_id=(UC[A-Za-z0-9_-]{20,})")
        )

        return patterns.asSequence()
            .mapNotNull{it.find(html)?.groupValues?.getOrNull(1)}
            .firstOrNull()
    }
}
