package tr.com.gundemradari.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ArticleDetails(
    val title:String,
    val description:String,
    val paragraphs:List<String>,
    val finalUrl:String,
    val host:String,
    val publishedAt:Long?
)

data class ResearchSummary(
    val whatHappened:List<String>,
    val whyImportant:List<String>,
    val latestSituation:List<String>
)

class ArticleReader {
    suspend fun read(url:String):ArticleDetails?=withContext(Dispatchers.IO){
        runCatching{
            val first=fetchDocument(url) ?: return@runCatching null
            val resolved=resolveIntermediary(first)
            val alternateUrls=extractAlternateUrls(resolved)

            var best=extractDetails(resolved)
            var bestScore=articleContentQuality(best)

            // İlk HTML yeterli metin vermiyorsa aynı makalenin canonical/AMP
            // sürümünü dener. Bu teyit değildir; aynı yayıncıdaki aynı haberin
            // daha okunabilir temsilini bulma adımıdır.
            if(bestScore<58.0){
                for(alt in alternateUrls.take(2)){
                    val altDoc=fetchDocument(alt) ?: continue
                    val candidate=extractDetails(altDoc)
                    val score=articleContentQuality(candidate)

                    if(score>bestScore+2.0){
                        best=candidate
                        bestScore=score
                    }
                    if(bestScore>=72.0)break
                }
            }

            best
        }.getOrNull()
    }

    private fun fetchDocument(url:String):Document?=
        runCatching{
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) GundemRadari/21")
                .timeout(14000)
                .followRedirects(true)
                .maxBodySize(2_500_000)
                .header("Accept-Language","tr-TR,tr;q=0.9,en;q=0.4")
                .get()
        }.getOrNull()

    private fun resolveIntermediary(first:Document):Document{
        if(!first.location().contains("news.google.com"))return first

        val external=first.select("a[href]").asSequence()
            .map{it.absUrl("href")}
            .filter{it.startsWith("http")}
            .filterNot{
                it.contains("google.com") ||
                it.contains("gstatic.com") ||
                it.contains("youtube.com")
            }
            .maxByOrNull{href->
                val path=runCatching{URI(href).path.orEmpty()}.getOrDefault("")
                path.length
            }

        return external?.let(::fetchDocument) ?: first
    }

    private fun extractAlternateUrls(doc:Document):List<String>{
        val current=doc.location()
        val currentHost=runCatching{
            URI(current).host.orEmpty().removePrefix("www.")
        }.getOrDefault("")

        val raw=listOf(
            doc.selectFirst("link[rel=amphtml]")?.absUrl("href").orEmpty(),
            doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty(),
            doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty()
        )

        return raw.asSequence()
            .map{it.trim()}
            .filter{it.startsWith("http")}
            .filterNot{sameNormalizedUrl(it,current)}
            .filter{candidate->
                val host=runCatching{
                    URI(candidate).host.orEmpty().removePrefix("www.")
                }.getOrDefault("")
                host.isNotBlank() &&
                (
                    currentHost.isBlank() ||
                    host==currentHost ||
                    host.endsWith("."+currentHost) ||
                    currentHost.endsWith("."+host)
                )
            }
            .distinct()
            .toList()
    }

    private fun extractDetails(doc:Document):ArticleDetails{
        val structured=extractStructuredArticle(doc)
        val working=doc.clone()

        val publishedAt=listOf(
            structured.datePublished,
            doc.selectFirst("meta[property=article:published_time]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[itemprop=datePublished]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=pubdate]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=publish-date]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=date]")?.attr("content").orEmpty(),
            doc.selectFirst("time[datetime]")?.attr("datetime").orEmpty()
        )
            .asSequence()
            .mapNotNull(::parseArticleDate)
            .firstOrNull()

        working.select(
            "script,style,nav,aside,footer,form,noscript,header,iframe,svg,"+
            ".advertisement,.advert,.ad,.ads,.cookie,.cookies,.newsletter,"+
            ".social-share,.share,.sharing,.related-news,.related-content,"+
            ".recommendation,.recommended,.most-read,.mostread,.sidebar,"+
            ".breadcrumb,.breadcrumbs,.tags,.tag-list,.author-box,.comments"
        ).remove()

        val ogTitle=doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val h1=working.selectFirst("h1")?.text().orEmpty()
        val title=listOf(structured.headline,ogTitle,h1,doc.title())
            .map(::cleanResearchText)
            .firstOrNull{it.length>=8}
            .orEmpty()

        val description=listOf(
            structured.description,
            doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=description]")?.attr("content").orEmpty(),
            working.selectFirst("article > p")?.text().orEmpty()
        )
            .map(::cleanResearchText)
            .firstOrNull{isUsefulResearchText(it)}
            .orEmpty()

        val structuredParas=structuredBodyParagraphs(structured.articleBody)
            .filter(::usableParagraph)

        val combinedSelector=listOf(
            "[itemprop=articleBody] p",
            "[itemprop=articleBody] li",
            "article p",
            "article li",
            ".article-body p",".article-content p",".article__body p",".article__content p",
            ".article-text p",".article-detail p",".article-detail-content p",
            ".news-content p",".news-detail p",".news-detail-content p",".news-text p",
            ".story-body p",".story-content p",".story__body p",
            ".entry-content p",".post-content p",".post__content p",
            ".content-detail p",".detail-content p",".detail__content p",
            "[class*=articleBody] p","[class*=article-body] p",
            "[class*=articleContent] p","[class*=article-content] p",
            "[class*=storyBody] p","[class*=story-body] p",
            "[data-testid*=article] p"
        ).joinToString(",")

        val selectedParas=working.select(combinedSelector)
            .asSequence()
            .map{cleanResearchText(it.text())}
            .filter(::usableParagraph)
            .distinctBy(::normalizeForDedup)
            .take(32)
            .toList()

        val denseParas=bestDenseContainerParagraphs(working)

        val mainParas=if(
            structuredParas.isEmpty() &&
            selectedParas.size<2
        ){
            working.select("main p").asSequence()
                .map{cleanResearchText(it.text())}
                .filter(::usableParagraph)
                .filterNot(::looksLikePeripheralParagraph)
                .distinctBy(::normalizeForDedup)
                .take(24)
                .toList()
        }else emptyList()

        val broadParas=if(
            structuredParas.isEmpty() &&
            selectedParas.size<2 &&
            denseParas.size<2 &&
            mainParas.size<2
        ){
            working.select("p").asSequence()
                .map{cleanResearchText(it.text())}
                .filter(::usableParagraph)
                .filterNot(::looksLikePeripheralParagraph)
                .distinctBy(::normalizeForDedup)
                .take(20)
                .toList()
        }else emptyList()

        val paras=(structuredParas+selectedParas+denseParas+mainParas+broadParas)
            .filterNot(::looksLikePeripheralParagraph)
            .distinctBy(::normalizeForDedup)
            .take(34)

        val canonical=doc.selectFirst("link[rel=canonical]")
            ?.absUrl("href")
            .orEmpty()
            .takeIf{it.startsWith("http")}
            ?:doc.location()

        return ArticleDetails(
            title=title,
            description=description,
            paragraphs=paras,
            finalUrl=canonical,
            host=runCatching{
                URI(canonical).host.orEmpty().removePrefix("www.")
            }.getOrDefault(""),
            publishedAt=publishedAt
        )
    }

    internal fun extractHtmlForTest(
        html:String,
        baseUrl:String="https://example.com/haber"
    ):ArticleDetails=
        extractDetails(Jsoup.parse(html,baseUrl))

    private fun bestDenseContainerParagraphs(doc:Document):List<String>{
        val candidates=doc.select(
            "article,main,[role=main],[itemprop=articleBody],"+
            "div[class*=article],div[class*=content],div[class*=story],"+
            "div[class*=news],div[class*=detail],div[class*=post],"+
            "section[class*=article],section[class*=content],section[class*=story]"
        )

        val best=candidates
            .asSequence()
            .map{container->
                val paragraphs=container.select("p").asSequence()
                    .map{cleanResearchText(it.text())}
                    .filter(::usableParagraph)
                    .filterNot(::looksLikePeripheralParagraph)
                    .distinctBy(::normalizeForDedup)
                    .take(32)
                    .toList()

                val chars=paragraphs.sumOf{it.length}
                val linkChars=container.select("a").sumOf{
                    cleanResearchText(it.text()).length
                }
                val score=
                    chars +
                    paragraphs.size*140 -
                    (linkChars*0.65).toInt()

                Triple(container,paragraphs,score)
            }
            .filter{(_,paragraphs,score)->
                paragraphs.size>=2 && score>=450
            }
            .maxByOrNull{it.third}

        return best?.second.orEmpty()
    }

    private fun usableParagraph(text:String):Boolean =
        text.length in 50..1800 &&
        isUsefulResearchText(text) &&
        !looksLikePeripheralParagraph(text)

    private fun looksLikePeripheralParagraph(text:String):Boolean{
        val lower=cleanResearchText(text)
            .lowercase(Locale("tr","TR"))

        val blocked=listOf(
            "ilgili haberler","diğer haberler","benzer haberler","çok okunanlar",
            "en çok okunanlar","son dakika haberleri","haberin devamı",
            "bu haber ilginizi çekebilir","etiketler:","kaynak:",
            "fotoğraf:","editör:","muhabir:","reklamdan sonra",
            "yorum yapmak için","yorumlarınızı","bizi takip edin",
            "whatsapp kanalımıza","telegram kanalımıza","google news'te"
        )

        if(blocked.any{lower.contains(it)})return true

        val words=lower.split(Regex("\\s+"))
        val linkish=words.count{
            it=="tıkla" || it=="oku" || it=="paylaş" || it=="takip"
        }

        return linkish>=3
    }

    private fun sameNormalizedUrl(a:String,b:String):Boolean{
        fun n(x:String)=x
            .substringBefore('#')
            .trimEnd('/')
            .replace(Regex("[?&](utm_[^=]+|fbclid|gclid)=[^&]+"),"")
        return n(a)==n(b)
    }
}


private data class StructuredArticle(
    val headline:String="",
    val description:String="",
    val articleBody:String="",
    val datePublished:String=""
)

private fun extractStructuredArticle(doc:Document):StructuredArticle{
    val candidates=mutableListOf<StructuredArticle>()

    doc.select("script[type=application/ld+json]").forEach{node->
        val rawCandidates=listOf(node.data(),node.html())
            .map{raw->
                raw.trim()
                    .removePrefix("<!--")
                    .removeSuffix("-->")
                    .replace("&quot;","\"")
                    .replace("&#34;","\"")
                    .replace("&amp;","&")
                    .trim()
            }
            .filter{it.isNotBlank()}
            .distinct()

        rawCandidates.forEach{raw->
            val objects=mutableListOf<JSONObject>()
            val root=runCatching{
                JSONTokener(raw).nextValue()
            }.getOrNull()
            collectJsonObjects(root,objects)

            objects
                .filter(::looksLikeArticleObject)
                .map(::structuredFromObject)
                .filter{it.hasUsefulData()}
                .forEach{candidates+=it}

            fallbackStructuredArticle(raw)
                ?.takeIf{it.hasUsefulData()}
                ?.let{candidates+=it}
        }
    }

    return candidates.maxByOrNull{item->
        item.articleBody.length+
        item.description.length*2+
        item.headline.length*3
    } ?: StructuredArticle()
}

private fun StructuredArticle.hasUsefulData():Boolean=
    headline.isNotBlank() ||
    description.isNotBlank() ||
    articleBody.isNotBlank()

private fun structuredFromObject(obj:JSONObject)=StructuredArticle(
    headline=jsonString(obj,"headline"),
    description=jsonString(obj,"description"),
    articleBody=jsonString(obj,"articleBody"),
    datePublished=jsonString(obj,"datePublished")
)

private fun fallbackStructuredArticle(raw:String):StructuredArticle?{
    fun capture(key:String):String{
        val pattern=Regex(
            """["]${Regex.escape(key)}["]\s*:\s*["]((?:\\.|[^"])*)["]""",
            setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)
        )
        val match=pattern.find(raw) ?: return ""

        val escaped=match.groupValues.getOrNull(1).orEmpty()
        return escaped
            .replace("\\n"," ")
            .replace("\\r"," ")
            .replace("\\t"," ")
            .replace("\\\"", "\"")
            .replace("\\/","/")
            .let(::cleanResearchText)
    }

    val item=StructuredArticle(
        headline=capture("headline"),
        description=capture("description"),
        articleBody=capture("articleBody"),
        datePublished=capture("datePublished")
    )

    return item.takeIf{it.hasUsefulData()}
}

private fun collectJsonObjects(value:Any?,out:MutableList<JSONObject>){
    when(value){
        is JSONObject->{
            out+=value
            val keys=value.keys()
            while(keys.hasNext()){
                val key=keys.next()
                collectJsonObjects(value.opt(key),out)
            }
        }
        is JSONArray->{
            for(i in 0 until value.length()){
                collectJsonObjects(value.opt(i),out)
            }
        }
    }
}

private fun looksLikeArticleObject(obj:JSONObject):Boolean{
    val type=obj.opt("@type")
    val types=when(type){
        is JSONArray->(0 until type.length()).mapNotNull{type.optString(it,null)}
        is String->listOf(type)
        else->emptyList()
    }
    return types.any{
        val x=it.lowercase(Locale.ROOT)
        x=="article" || x.contains("newsarticle") || x.contains("reportagenewsarticle")
    } || jsonString(obj,"articleBody").length>=180
}

private fun jsonString(obj:JSONObject,key:String):String{
    val raw=obj.optString(key,"")
    if(raw.isBlank())return ""
    val plain=if(raw.contains("<") && raw.contains(">")){
        Jsoup.parse(raw).text()
    }else raw
    return cleanResearchText(plain)
}

private fun structuredBodyParagraphs(body:String):List<String>{
    val clean=body
        .replace("\\r","\n")
        .replace(Regex("[\\t ]+")," ")
        .trim()
    if(clean.isBlank())return emptyList()

    val byLine=clean.split(Regex("\\n+"))
        .map(::cleanResearchText)
        .filter{it.length>=45}

    if(byLine.size>=2)return byLine.take(30)

    return clean
        .split(Regex("(?<=[.!?])\\s+"))
        .map(::cleanResearchText)
        .filter{it.length>=45}
        .chunked(2)
        .map{it.joinToString(" ")}
        .take(24)
}

private fun parseArticleDate(raw:String):Long?{
    val clean=raw.trim()
    if(clean.isBlank())return null

    runCatching{
        return Instant.parse(clean).toEpochMilli()
    }
    runCatching{
        return OffsetDateTime.parse(clean).toInstant().toEpochMilli()
    }
    runCatching{
        return ZonedDateTime.parse(clean).toInstant().toEpochMilli()
    }
    runCatching{
        return ZonedDateTime
            .parse(clean,DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
    }

    val localPatterns=listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    )
    for(format in localPatterns){
        runCatching{
            return LocalDateTime.parse(clean,format)
                .atZone(java.time.ZoneId.of("Europe/Istanbul"))
                .toInstant()
                .toEpochMilli()
        }
    }

    return null
}

private fun normalizeForDedup(text:String):String=
    cleanResearchText(text)
        .lowercase(Locale("tr","TR"))
        .replace(Regex("[^\\p{L}\\p{N}]+")," ")
        .take(220)

fun articleContentQuality(details:ArticleDetails):Double{
    val chars=details.paragraphs.sumOf{it.length}
    val usefulParagraphs=details.paragraphs.count{it.length>=90}
    val bodyScore=(chars/45.0).coerceAtMost(65.0)
    val paragraphScore=(usefulParagraphs*4.0).coerceAtMost(24.0)
    val descriptionScore=if(details.description.length>=90)8.0 else 0.0
    val titleScore=if(details.title.length>=20)3.0 else 0.0
    return (bodyScore+paragraphScore+descriptionScore+titleScore).coerceIn(0.0,100.0)
}

fun cleanResearchText(text:String):String =
    text
        .replace(Regex("\\s+")," ")
        .replace(' ',' ')
        .trim()

fun isUsefulResearchText(text:String):Boolean{
    val clean=cleanResearchText(text)
    if(clean.length<45)return false
    if(!looksTurkish(clean))return false

    val lower=clean.lowercase(Locale("tr","TR"))
    val blocked=listOf(
        "çerez","cookie","reklam","abonelik","abone ol","bildirimleri aç",
        "tüm hakları saklıdır","kişisel veriler","gizlilik politikası",
        "üyelik","giriş yap","uygulamamızı indir",
        "comprehensive up-to-date news coverage",
        "aggregated from sources all over the world",
        "google news","news.google.com",
        "javascript'i etkinleştirin","javascript etkinleştirin",
        "tarayıcınız desteklenmiyor"
    )
    return blocked.none{lower.contains(it)}
}

fun summarizeResearch(
    eventTitle:String,
    eventSummary:String,
    articles:List<ResearchedArticle>
):ResearchSummary{
    val locale=Locale("tr","TR")
    val stop=setOf(
        "göre","değil","geldi","olan","oldu","için","ile","dedi","son","yeni",
        "haber","açıklama","etti","eden","sonra","önce","olarak","daha","ancak",
        "bugün","şimdi","üzerine","ilişkin"
    )

    fun terms(text:String)=text.lowercase(locale)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter{it.length>3 && it !in stop}
        .toSet()

    val queryTerms=terms(eventTitle+" "+eventSummary.take(260))

    val actionMarkers=listOf(
        "açıkladı","duyurdu","bildirdi","söyledi","belirtti","karar verdi",
        "kabul edildi","reddedildi","başladı","başlatıldı","sona erdi","tamamlandı",
        "arttı","azaldı","yükseldi","düştü","ulaştı","imzalandı","yürürlüğe girdi",
        "gözaltına alındı","tutuklandı","serbest bırakıldı","iptal edildi",
        "kapatıldı","açıldı","gerçekleşti","meydana geldi","hayatını kaybetti",
        "yaralandı","seçildi","atandı","görevden alındı"
    )

    val impactMarkers=listOf(
        "etkiledi","etkileyecek","etkileyebilir","sonuç","sonucunda","nedeniyle",
        "risk","can kaybı","yaralı","hayatını kaybetti","ölü","iptal","kapatıldı",
        "yasak","ekonomi","piyasa","faiz","enflasyon","zam","vergi","seçim",
        "ülke genelinde","milyon","bin kişi","yıkım","hasar","kriz","güvenlik",
        "ulaşım","eğitim","sağlık","yürürlüğe","değişiklik","değiştirecek",
        "değiştiriyor","değiştirdi","etkilenecek","kapsayacak","kapsıyor",
        "maliyet","hak","yükümlülük","fiyat","ücret","gelir","işsizlik",
        "erişim","hizmet"
    )

    val causalMarkers=listOf(
        "bu nedenle","bu yüzden","dolayısıyla","böylece","sonucunda","nedeniyle",
        "etkisi","etkileri","yol aç","sebep","anlamına geliyor","sonuç olarak"
    )

    val latestMarkers=listOf(
        "son durum","son olarak","şu anda","halen","hâlen","devam ediyor",
        "devam etmekte","açıklandı","duyurdu","bildirdi","güncel","son açıklama",
        "arttı","yükseldi","düştü","ulaştı","başladı","sona erdi","tamamlandı",
        "gözaltına alındı","tutuklandı","serbest bırakıldı","bekleniyor",
        "sürüyor","sürdürüyor","yeniden","bu sabah","bu akşam"
    )

    val contextDependentPrefixes=listOf(
        "bu ","bunun ","bunların ","buna ","bunu ","bunları ",
        "ayrıca ","ancak ","öte yandan ","böylece ","dolayısıyla ",
        "söz konusu ","aynı zamanda ","bununla birlikte "
    )

    data class Candidate(
        val text:String,
        val whatScore:Double,
        val whyScore:Double,
        val latestScore:Double,
        val action:Boolean,
        val impact:Boolean,
        val latest:Boolean,
        val position:Int,
        val publishedAt:Long?
    )

    val candidates=mutableListOf<Candidate>()

    articles.forEach{article->
        val blocks=buildList{
            if(article.description.isNotBlank())add(article.description)
            addAll(article.paragraphs.take(26))
        }

        blocks.forEachIndexed{index,block->
            splitResearchSentences(block).forEach sentenceLoop@{sentence->
                if(sentence.length !in 45..420)return@sentenceLoop
                if(!isUsefulResearchText(sentence))return@sentenceLoop

                val polished=polishResearchSentence(sentence)
                if(polished.length<40)return@sentenceLoop

                val lower=polished.lowercase(locale)
                val sentenceTerms=terms(polished)
                val overlap=sentenceTerms.intersect(queryTerms).size

                val action=actionMarkers.any{lower.contains(it)}
                val impact=
                    impactMarkers.any{lower.contains(it)} ||
                    causalMarkers.any{lower.contains(it)}
                val latest=latestMarkers.any{lower.contains(it)}

                val positionBonus=when{
                    index==0->13.0
                    index<=2->10.0
                    index<=5->7.0
                    index<=10->3.5
                    else->1.0
                }

                val qualityBonus=article.quality
                    .coerceIn(0.0,100.0)/11.0
                val numericBonus=if(polished.any(Char::isDigit))3.0 else 0.0
                val lengthBonus=when(polished.length){
                    in 70..260->3.0
                    in 45..320->1.5
                    else->0.0
                }
                val contextPenalty=if(
                    contextDependentPrefixes.any{lower.startsWith(it)}
                ) 7.0 else 0.0

                val titleSimilarity=sentenceSimilarity(polished,eventTitle)
                val titlePenalty=when{
                    titleSimilarity>.86->10.0
                    titleSimilarity>.72->5.0
                    else->0.0
                }

                val base=
                    overlap*8.5+
                    positionBonus+
                    qualityBonus+
                    numericBonus+
                    lengthBonus-
                    contextPenalty-
                    titlePenalty

                val whatScore=
                    base+
                    (if(action)9.0 else 0.0)+
                    (if(overlap>0)3.0 else 0.0)

                val whyScore=
                    base*0.48+
                    (if(impact)20.0 else 0.0)+
                    (if(causalMarkers.any{lower.contains(it)})8.0 else 0.0)

                val latestScore=
                    base*0.55+
                    (if(latest)19.0 else 0.0)+
                    (if(action)6.0 else 0.0)+
                    (if(index<=8)3.0 else 0.0)

                candidates+=Candidate(
                    text=polished,
                    whatScore=whatScore,
                    whyScore=whyScore,
                    latestScore=latestScore,
                    action=action,
                    impact=impact,
                    latest=latest,
                    position=index,
                    publishedAt=article.publishedAt
                )
            }
        }
    }

    fun takeDistinct(
        input:List<Candidate>,
        count:Int,
        used:List<String> = emptyList()
    ):List<String>{
        val out=mutableListOf<String>()
        for(candidate in input){
            if(candidate.text.isBlank())continue
            if(used.any{sentenceSimilarity(it,candidate.text)>.50})continue
            if(out.none{sentenceSimilarity(it,candidate.text)>.50}){
                out+=candidate.text
                if(out.size>=count)break
            }
        }
        return out
    }

    val whatRanked=candidates.sortedWith(
        compareByDescending<Candidate>{it.whatScore}
            .thenBy{it.position}
    )

    val articleLeadFallback=articles
        .asSequence()
        .flatMap{article->
            sequenceOf(article.description)+
                article.paragraphs.take(5).asSequence()
        }
        .flatMap{splitResearchSentences(it).asSequence()}
        .map(::polishResearchSentence)
        .filter{
            it.length in 40..420 &&
            looksTurkish(it) &&
            !it.lowercase(locale).contains("google news")
        }
        .distinct()
        .take(1)
        .toList()

    val eventFallback=splitResearchSentences(eventSummary)
        .map(::polishResearchSentence)
        .filter{it.length>=35 && looksTurkish(it)}
        .take(1)

    val titleFallback=articles
        .map{polishResearchSentence(it.title)}
        .filter{it.length>=24 && looksTurkish(it)}
        .distinct()
        .take(1)

    val what=takeDistinct(whatRanked,1)
        .ifEmpty{articleLeadFallback}
        .ifEmpty{eventFallback}
        .ifEmpty{titleFallback}

    val whyRanked=candidates
        .filter{it.impact}
        .sortedByDescending{it.whyScore}

    var why=takeDistinct(whyRanked,1,what)
    if(why.isEmpty()){
        inferImportance(eventTitle,eventSummary,articles)?.let{
            why=listOf(it)
        }
    }

    val usedForLatest=what+why
    val latestExplicit=candidates
        .filter{it.latest}
        .sortedWith(
            compareByDescending<Candidate>{it.publishedAt?:0L}
                .thenByDescending{it.latestScore}
                .thenBy{it.position}
        )

    var latest=takeDistinct(latestExplicit,2,usedForLatest)

    if(latest.isEmpty()){
        val currentAction=candidates
            .filter{it.action}
            .sortedByDescending{it.latestScore}
        latest=takeDistinct(currentAction,1,usedForLatest)
    }

    if(latest.isEmpty()){
        val unusedFactual=candidates
            .sortedByDescending{it.latestScore}
        latest=takeDistinct(unusedFactual,1,usedForLatest)
    }

    return ResearchSummary(
        whatHappened=what,
        whyImportant=why,
        latestSituation=latest
    )
}

private fun splitResearchSentences(text:String):List<String> =
    cleanResearchText(text)
        .split(Regex("(?<=[.!?])\\s+|(?<=;)\\s+"))
        .map(::cleanResearchText)
        .filter{it.isNotBlank()}

private fun polishResearchSentence(text:String):String{
    var out=cleanResearchText(text)
        .replace(Regex("^[-•–—]+\\s*"),"")
        .replace(Regex("\\s+([,.;:!?])"),"$1")
        .trim()

    // Yaygın editoryal önekleri yalnız cümle başındaysa temizle.
    out=out.replace(
        Regex("^(son dakika|haber merkezi|editörün notu)\\s*[:|-]\\s*",
            RegexOption.IGNORE_CASE),
        ""
    ).trim()

    if(out.length>420)out=out.take(417).trimEnd()+"..."
    return out
}

private fun inferImportance(
    eventTitle:String,
    eventSummary:String,
    articles:List<ResearchedArticle>
):String?{
    val all=(eventTitle+" "+eventSummary+" "+
        articles.joinToString(" "){it.description+" "+it.paragraphs.take(8).joinToString(" ")})
        .lowercase(Locale("tr","TR"))

    return when{
        listOf("deprem","sel","yangın","heyelan","fırtına","can kaybı","yaralı")
            .any{all.contains(it)}->
            "Analiz: Gelişme, can güvenliği, ulaşım ve günlük yaşam üzerindeki doğrudan etkileri nedeniyle önem taşıyor."

        listOf("faiz","enflasyon","döviz","vergi","zam","bütçe","piyasa","asgari ücret")
            .any{all.contains(it)}->
            "Analiz: Gelişme, fiyatlar, piyasalar veya hane ve işletme maliyetleri üzerinde etkili olabileceği için önem taşıyor."

        listOf("yasa","kanun","yönetmelik","kararname","yürürlüğe","düzenleme")
            .any{all.contains(it)}->
            "Analiz: Gelişme, yürürlükteki kural veya uygulamaları etkileyebileceği için ilgili kişi ve kurumlar açısından önem taşıyor."

        listOf("seçim","oylama","meclis","hükümet","bakan","cumhurbaşkanı","başbakan")
            .any{all.contains(it)}->
            "Analiz: Gelişme, siyasi karar alma süreci ve tarafların sonraki adımları açısından önem taşıyor."

        listOf("saldırı","savaş","çatışma","ateşkes","operasyon","güvenlik")
            .any{all.contains(it)}->
            "Analiz: Gelişme, güvenlik durumu ve bölgedeki sonraki gelişmeler açısından önem taşıyor."

        else->null
    }
}

private fun sentenceSimilarity(a:String,b:String):Double{
    val x=a.lowercase(Locale("tr","TR")).split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>3}.toSet()
    val y=b.lowercase(Locale("tr","TR")).split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>3}.toSet()
    if(x.isEmpty()||y.isEmpty())return 0.0
    return x.intersect(y).size.toDouble()/x.union(y).size
}

data class ResearchedArticle(
    val sourceName:String,
    val title:String,
    val url:String,
    val description:String,
    val paragraphs:List<String>,
    val publishedAt:Long?,
    val isPrimary:Boolean,
    val quality:Double
)
