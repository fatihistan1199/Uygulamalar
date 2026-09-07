package tr.com.gundemradari.scan

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import tr.com.gundemradari.data.SourceEntity
import tr.com.gundemradari.religion.ReligionWebWatcher
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

data class FetchedItem(
    val source:SourceEntity,
    val url:String,
    val title:String,
    val summary:String,
    val publishedAt:Long?=null,
    val originalTitle:String=title,
    val originalSummary:String=summary
)

class FeedClient {
    private val religionWatcher=ReligionWebWatcher()

    suspend fun fetch(source:SourceEntity):List<FetchedItem> {
        if(source.kind=="religion_watch")return religionWatcher.fetch(source)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val conn=(URL(source.endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout=15000
                readTimeout=15000
                setRequestProperty("User-Agent","Mozilla/5.0 (Android) GundemRadari/0.8")
                setRequestProperty("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.7")
                instanceFollowRedirects=true
            }
            try {
                if(conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val text=conn.inputStream.bufferedReader().readText()
                when(source.kind){
                    "telegram" -> parseTelegram(text,source)
                    else -> parseXml(text,source)
                }
            } finally { conn.disconnect() }
        }
    }

    private fun parseXml(xml:String,source:SourceEntity):List<FetchedItem>{
        val parser=Xml.newPullParser()
        parser.setInput(xml.reader())
        val out=mutableListOf<FetchedItem>()
        var tag=""
        var title=""
        var link=""
        var summary=""
        var published=""
        var inEntry=false
        while(parser.eventType!=XmlPullParser.END_DOCUMENT){
            when(parser.eventType){
                XmlPullParser.START_TAG->{
                    tag=parser.name.lowercase()
                    if(tag=="item"||tag=="entry"){
                        inEntry=true; title=""; link=""; summary=""; published=""
                    }
                    if(inEntry&&tag=="link"){
                        val href=parser.getAttributeValue(null,"href")
                        val rel=parser.getAttributeValue(null,"rel")
                        if(!href.isNullOrBlank()&&(rel.isNullOrBlank()||rel=="alternate")) link=href
                    }
                }
                XmlPullParser.TEXT-> if(inEntry){
                    when(tag){
                        "title"->title+=parser.text
                        "link"->if(link.isBlank())link+=parser.text
                        "description","summary","content","encoded"->summary+=parser.text
                        "pubdate","published","updated","date"->published+=parser.text
                    }
                }
                XmlPullParser.END_TAG-> if(parser.name.lowercase()=="item"||parser.name.lowercase()=="entry"){
                    val cleanTitle=decode(title)
                    val cleanSummary=decode(summary).take(700)
                    if(cleanTitle.isNotBlank()&&link.isNotBlank()){
                        out+=FetchedItem(source,link.trim(),cleanTitle,cleanSummary,parseDate(published.trim()))
                    }
                    inEntry=false
                }
            }
            parser.next()
        }
        return out.take(40)
    }

    private fun parseTelegram(html:String,source:SourceEntity):List<FetchedItem>{
        val out=mutableListOf<FetchedItem>()
        val chunks=html.split("data-post=\"").drop(1)
        for(chunk in chunks){
            val post=chunk.substringBefore('"').trim()
            if(post.isBlank()||!post.contains('/')) continue
            val message=Regex("<div class=\"tgme_widget_message_text[^\"]*[^>]*>(.*?)</div>",RegexOption.DOT_MATCHES_ALL)
                .find(chunk)?.groupValues?.getOrNull(1) ?: continue
            val text=decode(message)
            if(text.isBlank()) continue
            val datetime=Regex("<time[^>]+datetime=\"([^\"]+)\"").find(chunk)?.groupValues?.getOrNull(1)
            val firstLine=text.lineSequence().map{it.trim()}.firstOrNull{it.isNotBlank()}.orEmpty()
            val title=firstLine.take(220).ifBlank{text.take(220)}
            out+=FetchedItem(
                source=source,
                url="https://t.me/$post",
                title=title,
                summary=text.take(700),
                publishedAt=parseDate(datetime.orEmpty())
            )
        }
        return out.distinctBy{it.url}.take(40)
    }

    private fun decode(s:String):String = Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString()
        .replace('\u00a0',' ')
        .replace(Regex("[ \\t]+")," ")
        .replace(Regex("\\n{3,}"),"\n\n")
        .trim()

    private fun parseDate(raw:String):Long?{
        if(raw.isBlank()) return null
        runCatching{return ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()}
        runCatching{return OffsetDateTime.parse(raw).toInstant().toEpochMilli()}
        val patterns=listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for(pattern in patterns){
            val f=SimpleDateFormat(pattern,Locale.ENGLISH).apply{timeZone=TimeZone.getTimeZone("UTC");isLenient=true}
            runCatching{f.parse(raw)?.time}.getOrNull()?.let{return it}
        }
        return null
    }
}

fun exactHash(title:String,url:String):String=MessageDigest.getInstance("SHA-256")
    .digest((title.lowercase()+"|"+url.substringBefore('?')).toByteArray())
    .joinToString(""){"%02x".format(it)}
