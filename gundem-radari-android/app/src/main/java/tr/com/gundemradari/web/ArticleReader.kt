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
            "[data-testid*=article] p",
            "main p"
        ).joinToString(",")

        val selectedParas=working.select(combinedSelector)
            .asSequence()
            .map{cleanResearchText(it.text())}
            .filter(::usableParagraph)
            .distinctBy(::normalizeForDedup)
            .take(32)
            .toList()

        val denseParas=bestDenseContainerParagraphs(working)

        val broadParas=if(
            structuredParas.isEmpty() &&
            selectedParas.size<2 &&
            denseParas.size<2
        ){
            working.select("p").asSequence()
                .map{cleanResearchText(it.text())}
                .filter(::usableParagraph)
                .filterNot(::looksLikePeripheralParagraph)
                .distinctBy(::normalizeForDedup)
                .take(20)
                .toList()
        }else emptyList()

        val paras=(structuredParas+selectedParas+denseParas+broadParas)
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
    val objects=mutableListOf<JSONObject>()

    doc.select("script[type=application/ld+json]").forEach{node->
        val raw=node.data().ifBlank{node.html()}.trim()
        if(raw.isBlank())return@forEach
        val root=runCatching{JSONTokener(raw).nextValue()}.getOrNull()
        collectJsonObjects(root,objects)
    }

    val best=objects
        .filter(::looksLikeArticleObject)
        .maxByOrNull{obj->
            jsonString(obj,"articleBody").length +
            jsonString(obj,"description").length*2 +
            jsonString(obj,"headline").length*3
        }
        ?:return StructuredArticle()

    return StructuredArticle(
        headline=jsonString(best,"headline"),
        description=jsonString(best,"description"),
        articleBody=jsonString(best,"articleBody"),
        datePublished=jsonString(best,"datePublished")
    )
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

private fun jsonString(obj:JSONObject,key:String):String=
    obj.optString(key,"").let(::cleanResearchText)

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
        "haber","açıklama","etti","eden","sonra","önce","olarak","daha","ancak"
    )

    fun terms(text:String)=text.lowercase(locale)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter{it.length>3 && it !in stop}
        .toSet()

    val queryTerms=terms(eventTitle+" "+eventSummary.take(220))

    val impactMarkers=listOf(
        "etkiledi","etkileyecek","etkileyebilir","sonuç","sonucunda","nedeniyle",
        "risk","can kaybı","yaralı","hayatını kaybetti","ölü","iptal","kapatıldı",
        "yasak","ekonomi","piyasa","faiz","enflasyon","zam","vergi","seçim",
        "ülke genelinde","milyon","bin kişi","yıkım","hasar","kriz","güvenlik",
        "ulaşım","eğitim","sağlık","yürürlüğe","değişiklik","maliyet"
    )
    val causalMarkers=listOf(
        "bu nedenle","bu yüzden","dolayısıyla","böylece","sonucunda","nedeniyle",
        "etkisi","etkileri","yol aç","sebep","anlamına geliyor"
    )
    val latestMarkers=listOf(
        "son durum","son olarak","bugün","şu anda","halen","hâlen","devam ediyor",
        "devam etmekte","açıklandı","duyurdu","bildirdi","güncel","son açıklama",
        "arttı","yükseldi","düştü","ulaştı","başladı","sona erdi","tamamlandı",
        "gözaltına alındı","tutuklandı","serbest bırakıldı"
    )

    data class Candidate(
        val text:String,
        val score:Double,
        val impact:Boolean,
        val latest:Boolean,
        val position:Int,
        val quality:Double,
        val publishedAt:Long?
    )

    val candidates=mutableListOf<Candidate>()

    articles.forEach{article->
        val blocks=buildList{
            if(article.description.isNotBlank())add(article.description)
            addAll(article.paragraphs.take(22))
        }

        blocks.forEachIndexed{index,block->
            splitResearchSentences(block).forEach{sentence->
                if(sentence.length !in 45..420 || !isUsefulResearchText(sentence))return@forEach

                val lower=sentence.lowercase(locale)
                val overlap=terms(sentence).intersect(queryTerms).size
                val positionBonus=when{
                    index==0->12.0
                    index<=3->8.0
                    index<=7->4.0
                    else->1.0
                }
                val numericBonus=if(sentence.any(Char::isDigit))2.5 else 0.0
                val lengthBonus=if(sentence.length in 75..260)2.0 else 0.0
                val qualityBonus=(article.quality.coerceIn(0.0,100.0)/10.0)
                val score=overlap*8.0+positionBonus+numericBonus+lengthBonus+qualityBonus

                // Tam başlık tekrarını özet diye göstermemek için küçük ceza.
                val titlePenalty=if(sentenceSimilarity(sentence,eventTitle)>.78)7.0 else 0.0

                candidates+=Candidate(
                    text=polishResearchSentence(sentence),
                    score=score-titlePenalty,
                    impact=impactMarkers.any{lower.contains(it)} ||
                        causalMarkers.any{lower.contains(it)},
                    latest=latestMarkers.any{lower.contains(it)},
                    position=index,
                    quality=article.quality,
                    publishedAt=article.publishedAt
                )
            }
        }
    }

    fun distinctTake(
        input:List<Candidate>,
        count:Int,
        used:List<String> = emptyList()
    ):List<String>{
        val out=mutableListOf<String>()
        for(c in input){
            if(c.text.isBlank())continue
            if(used.any{sentenceSimilarity(it,c.text)>.54})continue
            if(out.none{sentenceSimilarity(it,c.text)>.54}){
                out+=c.text
                if(out.size>=count)break
            }
        }
        return out
    }

    val ranked=candidates.sortedWith(
        compareByDescending<Candidate>{it.score}
            .thenBy{it.position}
            .thenByDescending{it.quality}
    )

    val articleFallback=articles
        .asSequence()
        .flatMap{article->
            sequenceOf(article.description)+
                article.paragraphs.take(4).asSequence()
        }
        .flatMap{splitResearchSentences(it).asSequence()}
        .map(::polishResearchSentence)
        .filter{
            it.length in 30..420 &&
            looksTurkish(it) &&
            !it.lowercase(locale).contains("google news")
        }
        .distinct()
        .take(2)
        .toList()

    val fallbackWhat=splitResearchSentences(eventSummary)
        .map(::polishResearchSentence)
        .filter{it.length>=30 && looksTurkish(it)}
        .take(2)

    val titleFallback=articles
        .map{polishResearchSentence(it.title)}
        .filter{it.length>=24 && looksTurkish(it)}
        .distinct()
        .take(1)

    val what=distinctTake(ranked,2)
        .ifEmpty{articleFallback}
        .ifEmpty{fallbackWhat}
        .ifEmpty{titleFallback}

    val whyCandidates=candidates
        .filter{it.impact}
        .sortedWith(
            compareByDescending<Candidate>{it.score+4.0}
                .thenBy{it.position}
        )
    var why=distinctTake(whyCandidates,2,what)
    if(why.isEmpty()){
        inferImportance(eventTitle,eventSummary,articles)?.let{why=listOf(it)}
    }

    val used=what+why
    val latestCandidates=candidates
        .filter{it.latest}
        .sortedWith(
            compareByDescending<Candidate>{it.publishedAt?:0L}
                .thenByDescending{it.score}
                .thenBy{it.position}
        )

    var latest=distinctTake(latestCandidates,2,used)

    // Açık "son durum" işareti yoksa aynı güncel makaledeki güçlü, kullanılmamış
    // ek bilgiyi göster; boş bir bölüm bırakmaktan daha yararlıdır.
    if(latest.isEmpty()){
        latest=distinctTake(
            ranked.filter{it.position<=8},
            1,
            used
        )
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
