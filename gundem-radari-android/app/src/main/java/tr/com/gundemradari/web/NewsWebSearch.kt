package tr.com.gundemradari.web

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class WebNewsResult(
    val title:String,
    val source:String,
    val url:String,
    val snippet:String,
    val publishedAt:Long?
)

private data class NewsSearchCacheEntry(
    val createdAt:Long,
    val rows:List<WebNewsResult>
)

private const val NEWS_SEARCH_CACHE_TTL_MS=5L*60*1000
private val newsSearchCache=ConcurrentHashMap<String,NewsSearchCacheEntry>()

class NewsWebSearch {
    suspend fun search(
        query:String,
        limit:Int=10,
        expandDescriptions:Boolean=false,
        maxAgeDays:Int?=null
    ):List<WebNewsResult>{
        val compact=compactQuery(query)
        if(compact.isBlank())return emptyList()

        val cacheKey="${compact.lowercase(Locale("tr","TR"))}|$limit|$expandDescriptions|${maxAgeDays?:0}"
        val now=System.currentTimeMillis()
        newsSearchCache[cacheKey]?.let{entry->
            if(now-entry.createdAt<NEWS_SEARCH_CACHE_TTL_MS){
                return entry.rows
            }
        }

        val rows=withContext(Dispatchers.IO){
            val cutoff=maxAgeDays?.let{
                System.currentTimeMillis()-it.coerceIn(1,365)*24L*60*60*1000
            }

            fun freshRelevant(rows:List<WebNewsResult>)=rows
                .filter{row->
                    cutoff==null ||
                    (
                        row.publishedAt!=null &&
                        row.publishedAt>=cutoff &&
                        row.publishedAt<=System.currentTimeMillis()+24L*60*60*1000
                    )
                }
                .filter{row->isSearchRelevant(compact,row)}
                .distinctBy{row->
                    normalizeSearchTitle(row.title)
                }
                .take(limit)

            fun bingSearch(qText:String):List<WebNewsResult>{
                val q=URLEncoder.encode(
                    qText,
                    StandardCharsets.UTF_8.toString()
                )
                val endpoint=
                    "https://www.bing.com/news/search?q=$q&format=rss"+
                    "&setmkt=tr-TR&cc=TR&qft=sortbydate%3d%221%22"
                return download(endpoint)
                    ?.let(::parse)
                    ?.let(::freshRelevant)
                    .orEmpty()
            }

            val variants=searchVariants(compact)
            val direct=mutableListOf<WebNewsResult>()

            for(variant in variants){
                direct+=bingSearch(variant)
                val dedup=direct
                    .distinctBy{normalizeSearchTitle(it.title)}
                direct.clear()
                direct+=dedup
                if(direct.size>=minOf(limit,4))break
            }

            val parsed=if(direct.isNotEmpty()){
                direct.take(limit)
            }else{
                // Google News yalnız son keşif yedeğidir. Tarih ve konu
                // uygunluğu yine uygulama içinde denetlenir.
                val searchText=if(maxAgeDays!=null){
                    "$compact when:${maxAgeDays.coerceIn(1,365)}d"
                }else compact
                val googleQ=URLEncoder.encode(
                    searchText,
                    StandardCharsets.UTF_8.toString()
                )
                val googleEndpoint=
                    "https://news.google.com/rss/search?q=$googleQ&hl=tr&gl=TR&ceid=TR:tr"
                download(googleEndpoint)
                    ?.let(::parse)
                    ?.let(::freshRelevant)
                    .orEmpty()
            }

            if(!expandDescriptions){
                parsed
            }else{
                coroutineScope{
                    parsed.mapIndexed{index,row->
                        async{
                            if(index<5 && !row.url.contains("news.google.com")){
                                val meta=fetchMetaDescription(row.url)
                                if(!meta.isNullOrBlank()){
                                    row.copy(snippet=meta)
                                }else row
                            }else row
                        }
                    }.awaitAll()
                }
            }
        }

        newsSearchCache[cacheKey]=
            NewsSearchCacheEntry(now,rows)

        if(newsSearchCache.size>48){
            newsSearchCache.entries
                .sortedBy{it.value.createdAt}
                .take(newsSearchCache.size-36)
                .forEach{
                    newsSearchCache.remove(it.key)
                }
        }

        return rows
    }

    private fun compactQuery(query:String):String{
        val clean=query
            .replace(Regex("[“”\"'’]")," ")
            .replace(
                Regex("[^\\p{L}\\p{N}\\s-]"),
                " "
            )
            .replace(Regex("\\s+")," ")
            .trim()

        val stop=setOf(
            "the","a","an","and","or","but",
            "why","how","what","when","where","who",
            "is","are","was","were","be","been","being",
            "to","of","for","in","on","at","with","from",
            "as","by","its","it","this","that","these","those",
            "ve","veya","ile","için","bu","şu",
            "neden","nasıl","olan","olarak","bir"
        )

        val tokens=clean
            .split(" ")
            .filter{
                it.length>2 &&
                it.lowercase(Locale.ROOT) !in stop
            }

        return tokens
            .take(10)
            .joinToString(" ")
            .ifBlank{clean.take(140)}
    }

    private fun searchVariants(compact:String):List<String>{
        val tokens=compact.split(" ").filter{it.isNotBlank()}
        if(tokens.size<=6)return listOf(compact)

        val first=compact
        val shorter=tokens.take(6).joinToString(" ")
        val entityHeavy=tokens
            .filterIndexed{index,token->
                index<4 || token.firstOrNull()?.isUpperCase()==true || token.any(Char::isDigit)
            }
            .take(7)
            .joinToString(" ")

        return listOf(first,shorter,entityHeavy)
            .map{it.trim()}
            .filter{it.isNotBlank()}
            .distinct()
    }

    private fun normalizeSearchTitle(text:String):String=
        cleanResearchText(text)
            .lowercase(Locale("tr","TR"))
            .replace(Regex("[^\\p{L}\\p{N}]+")," ")
            .trim()

    private fun isSearchRelevant(
        query:String,
        row:WebNewsResult
    ):Boolean{
        val qTokens=searchTokens(query)
        if(qTokens.isEmpty())return true

        val haystack=searchTokens(
            row.title+" "+row.snippet.take(500)
        )
        if(haystack.isEmpty())return false

        val overlap=qTokens.count{q->
            haystack.any{h->tokenRelated(q,h)}
        }

        val titleTokens=searchTokens(row.title)
        val titleOverlap=qTokens.count{q->
            titleTokens.any{h->tokenRelated(q,h)}
        }

        return when{
            qTokens.size==1->overlap>=1
            qTokens.size==2->titleOverlap>=1 || overlap>=2
            qTokens.size<=4->titleOverlap>=1 && overlap>=2
            else->overlap>=2 && (titleOverlap>=1 || overlap.toDouble()/qTokens.size>=0.34)
        }
    }

    private fun searchTokens(text:String):Set<String>{
        val stop=setOf(
            "haber","son","yeni","dedi","etti","olan","olarak","göre",
            "için","ile","bir","ve","veya","ancak","sonra","önce"
        )
        return text
            .lowercase(Locale("tr","TR"))
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter{it.length>=3 && it !in stop}
            .toSet()
    }

    private fun tokenRelated(a:String,b:String):Boolean{
        if(a==b)return true
        if(a.length<5 || b.length<5)return false
        val min=minOf(a.length,b.length)
        val common=minOf(min,7)
        return a.take(common)==b.take(common)
    }

    private fun download(url:String):String?{
        val conn=(
            URL(url).openConnection()
                as HttpURLConnection
        ).apply{
            connectTimeout=12000
            readTimeout=12000
            instanceFollowRedirects=true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) GundemRadari/21"
            )
            setRequestProperty(
                "Accept",
                "application/rss+xml,application/xml,text/xml,*/*"
            )
        }

        return try{
            if(conn.responseCode !in 200..299){
                null
            }else{
                conn.inputStream
                    .bufferedReader()
                    .use{it.readText()}
            }
        }catch(_:Throwable){
            null
        }finally{
            conn.disconnect()
        }
    }

    private fun parse(xml:String):List<WebNewsResult>{
        val parser=Xml.newPullParser()
        parser.setInput(xml.reader())

        val out=mutableListOf<WebNewsResult>()
        var tag=""
        var title=""
        var link=""
        var description=""
        var source=""
        var published=""
        var inItem=false

        while(
            parser.eventType!=
            XmlPullParser.END_DOCUMENT
        ){
            when(parser.eventType){
                XmlPullParser.START_TAG->{
                    tag=parser.name.lowercase(Locale.ROOT)
                    if(tag=="item"){
                        inItem=true
                        title=""
                        link=""
                        description=""
                        source=""
                        published=""
                    }
                }

                XmlPullParser.TEXT->if(inItem){
                    when(tag){
                        "title"->title+=parser.text
                        "link"->link+=parser.text
                        "description"->description+=parser.text
                        "source"->source+=parser.text
                        "pubdate","published","updated"->
                            published+=parser.text
                    }
                }

                XmlPullParser.END_TAG->
                    if(parser.name.equals("item",true)){
                        val cleanTitle=clean(title)
                            .let{
                                removeSourceSuffix(
                                    it,
                                    source
                                )
                            }

                        val rawSnippet=
                            clean(description)
                                .replace(
                                    cleanTitle,
                                    "",
                                    ignoreCase=true
                                )
                                .replace(
                                    source.trim(),
                                    "",
                                    ignoreCase=true
                                )
                                .trim(
                                    ' ',
                                    '-',
                                    '·',
                                    '|'
                                )
                                .take(900)

                        val cleanSnippet=
                            cleanResearchText(rawSnippet)
                                .takeIf(
                                    ::isUsefulResearchText
                                )
                                .orEmpty()

                        if(
                            cleanTitle.isNotBlank() &&
                            link.isNotBlank()
                        ){
                            out+=WebNewsResult(
                                title=cleanTitle,
                                source=source.trim()
                                    .ifBlank{
                                        publisherFromTitle(
                                            title
                                        )
                                    },
                                url=link.trim(),
                                snippet=cleanSnippet,
                                publishedAt=
                                    parseDate(
                                        published.trim()
                                    )
                            )
                        }

                        inItem=false
                    }
            }

            parser.next()
        }

        return out.distinctBy{
            it.title.lowercase(
                Locale("tr","TR")
            )
        }
    }

    private fun removeSourceSuffix(
        title:String,
        source:String
    ):String{
        val src=source.trim()

        if(
            src.isNotBlank() &&
            title.endsWith(
                " - $src",
                ignoreCase=true
            )
        ){
            return title
                .dropLast(src.length+3)
                .trim()
        }

        return title
    }

    private fun publisherFromTitle(
        title:String
    ):String =
        title
            .substringAfterLast(" - ","")
            .trim()

    private fun clean(text:String):String =
        Html.fromHtml(
            text,
            Html.FROM_HTML_MODE_LEGACY
        )
            .toString()
            .replace('\u00a0',' ')
            .replace(Regex("[ \\t]+")," ")
            .replace(Regex("\\n{2,}")," ")
            .trim()

    private fun fetchMetaDescription(
        url:String
    ):String?{
        val conn=(
            URL(url).openConnection()
                as HttpURLConnection
        ).apply{
            connectTimeout=10000
            readTimeout=10000
            instanceFollowRedirects=true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) GundemRadari/21"
            )
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml"
            )
        }

        return try{
            if(conn.responseCode !in 200..399){
                return null
            }

            val html=
                conn.inputStream
                    .bufferedReader()
                    .use{reader->
                        val sb=StringBuilder()
                        val buf=CharArray(4096)

                        while(sb.length<180000){
                            val n=reader.read(buf)
                            if(n<=0)break
                            sb.append(buf,0,n)
                        }

                        sb.toString()
                    }

            val patterns=listOf(
                Regex(
                    """<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:description["']""",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    """<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    """<meta[^>]+content=["']([^"']+)["'][^>]+name=["']description["']""",
                    RegexOption.IGNORE_CASE
                )
            )

            patterns
                .firstNotNullOfOrNull{
                    it.find(html)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                ?.let(::cleanResearchText)
                ?.takeIf(::isUsefulResearchText)
                ?.take(900)

        }catch(_:Throwable){
            null
        }finally{
            conn.disconnect()
        }
    }

    private fun parseDate(raw:String):Long?{
        if(raw.isBlank())return null

        runCatching{
            return ZonedDateTime
                .parse(
                    raw,
                    DateTimeFormatter.RFC_1123_DATE_TIME
                )
                .toInstant()
                .toEpochMilli()
        }

        runCatching{
            return OffsetDateTime
                .parse(raw)
                .toInstant()
                .toEpochMilli()
        }

        val patterns=listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z"
        )

        for(pattern in patterns){
            val f=SimpleDateFormat(
                pattern,
                Locale.ENGLISH
            ).apply{
                timeZone=
                    TimeZone.getTimeZone("UTC")
                isLenient=true
            }

            runCatching{
                f.parse(raw)?.time
            }
                .getOrNull()
                ?.let{
                    return it
                }
        }

        return null
    }
}

fun looksTurkish(text:String):Boolean{
    if(text.any{it in "çğıöşüÇĞİÖŞÜ"}){
        return true
    }

    val lower=
        " "+text.lowercase(
            Locale("tr","TR")
        )+" "

    val common=listOf(
        " ve "," için "," ile "," bir ",
        " bu "," son "," göre "," değil ",
        " olarak "," ancak "," başkanı ",
        " açıklama "," türkiye "," iran ",
        " israil "," oldu "," etti "," dedi "
    )

    return common.count{
        lower.contains(it)
    }>=2
}

fun buildExtractiveWebSummary(
    rows:List<WebNewsResult>
):List<String>{
    val candidates=mutableListOf<String>()

    rows.forEach{row->
        val snippet=
            cleanResearchText(row.snippet)

        if(
            snippet.length>=55 &&
            isUsefulResearchText(snippet)
        ){
            snippet
                .split(
                    Regex("(?<=[.!?])\\s+")
                )
                .map(::cleanResearchText)
                .filter{
                    it.length in 45..260 &&
                    isUsefulResearchText(it)
                }
                .forEach{
                    candidates+=it
                }
        }
    }

    if(candidates.isEmpty()){
        return rows
            .filter{
                looksTurkish(it.title)
            }
            .map{it.title}
            .distinct()
            .take(4)
    }

    val selected=
        mutableListOf<String>()

    for(
        sentence in
        candidates.sortedByDescending{
            it.length
        }
    ){
        if(
            selected.none{
                simpleSimilarity(
                    it,
                    sentence
                )>.58
            }
        ){
            selected+=sentence
            if(selected.size==4)break
        }
    }

    return selected
}

private fun simpleSimilarity(
    a:String,
    b:String
):Double{
    val x=a
        .lowercase(Locale("tr","TR"))
        .split(
            Regex("[^\\p{L}\\p{N}]+")
        )
        .filter{it.length>3}
        .toSet()

    val y=b
        .lowercase(Locale("tr","TR"))
        .split(
            Regex("[^\\p{L}\\p{N}]+")
        )
        .filter{it.length>3}
        .toSet()

    if(
        x.isEmpty() ||
        y.isEmpty()
    ){
        return 0.0
    }

    return x
        .intersect(y)
        .size
        .toDouble() /
        x.union(y).size
}
