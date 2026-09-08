package tr.com.gundemradari.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI
import java.util.Locale

data class ArticleDetails(
    val title:String,
    val description:String,
    val paragraphs:List<String>,
    val finalUrl:String,
    val host:String
)

data class ResearchSummary(
    val whatHappened:List<String>,
    val whyImportant:List<String>,
    val latestSituation:List<String>
)

class ArticleReader {
    suspend fun read(url:String):ArticleDetails?=withContext(Dispatchers.IO){
        runCatching{
            val first=Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) GundemRadari/17")
                .timeout(14000)
                .followRedirects(true)
                .get()

            val doc=if(first.location().contains("news.google.com")){
                val external=first.select("a[href]").asSequence()
                    .map{it.absUrl("href")}
                    .firstOrNull{href->
                        href.startsWith("http") &&
                        !href.contains("google.com") &&
                        !href.contains("gstatic.com") &&
                        !href.contains("youtube.com")
                    }
                if(external!=null){
                    runCatching{
                        Jsoup.connect(external)
                            .userAgent("Mozilla/5.0 (Android) GundemRadari/17")
                            .timeout(14000)
                            .followRedirects(true)
                            .get()
                    }.getOrElse{first}
                }else first
            }else first

            doc.select("script,style,nav,aside,footer,form,noscript,header,.advertisement,.ad,.ads,.cookie,.newsletter").remove()

            val ogTitle=doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
            val h1=doc.selectFirst("h1")?.text().orEmpty()
            val title=listOf(ogTitle,h1,doc.title()).firstOrNull{it.isNotBlank()}.orEmpty().trim()

            val description=listOf(
                doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
                doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
            )
                .map(::cleanResearchText)
                .firstOrNull{isUsefulResearchText(it)}
                .orEmpty()

            val selectors=listOf(
                "article p","[itemprop=articleBody] p",".article-body p",".article-content p",
                ".news-content p",".story-body p",".content-detail p",".detail-content p","main p"
            )

            val paras=selectors.asSequence()
                .flatMap{sel->doc.select(sel).asSequence()}
                .map{it.text().replace(Regex("\\s+")," ").trim()}
                .filter{usableParagraph(it)}
                .distinct()
                .take(24)
                .toList()
                .ifEmpty{
                    doc.select("body p").asSequence()
                        .map{it.text().replace(Regex("\\s+")," ").trim()}
                        .filter{usableParagraph(it)}
                        .distinct()
                        .take(16)
                        .toList()
                }

            ArticleDetails(
                title=title,
                description=description,
                paragraphs=paras,
                finalUrl=doc.location(),
                host=runCatching{URI(doc.location()).host.orEmpty().removePrefix("www.")}.getOrDefault("")
            )
        }.getOrNull()
    }

    private fun usableParagraph(text:String):Boolean =
        text.length in 50..1100 && isUsefulResearchText(text)
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
    val queryStop=setOf(
        "göre","değil","geldi","olan","oldu","için","ile","dedi","son",
        "yeni","haber","açıklama","etti","eden","sonra","önce"
    )
    val queryTerms=eventTitle.lowercase(locale)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter{it.length>3 && it !in queryStop}
        .toSet()

    val impactMarkers=listOf(
        "etkiledi","etkileyecek","sonuç","nedeniyle","risk","can kaybı","yaralı","ölü",
        "iptal","kapatıldı","yasak","ekonomi","piyasa","faiz","seçim","ülke genelinde",
        "milyon","bin kişi","yıkım","hasar","kriz","güvenlik"
    )
    val latestMarkers=listOf(
        "son durum","son olarak","bugün","şu anda","halen","devam ediyor","açıklandı",
        "duyurdu","bildirdi","güncel","son açıklama","arttı","yükseldi","düştü","ulaştı"
    )

    data class Candidate(
        val text:String,
        val relevance:Int,
        val impact:Boolean,
        val latest:Boolean,
        val quality:Double,
        val publishedAt:Long?,
        val sourceIndex:Int
    )

    val candidates=mutableListOf<Candidate>()
    articles.forEachIndexed{articleIndex,article->
        val blocks=buildList{
            if(article.description.isNotBlank())add(article.description)
            addAll(article.paragraphs.take(14))
        }

        blocks.forEachIndexed{index,block->
            block.split(Regex("(?<=[.!?])\\s+"))
                .map{it.replace(Regex("\\s+")," ").trim()}
                .map(::cleanResearchText)
                .filter{it.length in 55..360 && isUsefulResearchText(it)}
                .forEach{sentence->
                    val lower=sentence.lowercase(locale)
                    val terms=lower
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter{it.length>3}
                        .toSet()
                    val overlap=terms.intersect(queryTerms).size
                    if(overlap==0)return@forEach
                    val relevance=
                        overlap*9 +
                        if(index<4)4 else 0 +
                        if(sentence.any(Char::isDigit))2 else 0
                    candidates+=Candidate(
                        text=sentence,
                        relevance=relevance,
                        impact=impactMarkers.any{lower.contains(it)},
                        latest=latestMarkers.any{lower.contains(it)},
                        quality=article.quality,
                        publishedAt=article.publishedAt,
                        sourceIndex=articleIndex
                    )
                }
        }
    }

    fun distinctTake(input:List<Candidate>,count:Int,used:Set<String> = emptySet()):List<String>{
        val out=mutableListOf<String>()
        for(c in input){
            if(c.text in used)continue
            if(out.none{sentenceSimilarity(it,c.text)>.50}){
                out+=c.text
                if(out.size>=count)break
            }
        }
        return out
    }

    fun support(candidate:Candidate):Int =
        candidates
            .asSequence()
            .filter{
                it.sourceIndex!=candidate.sourceIndex
            }
            .count{
                sentenceSimilarity(
                    it.text,
                    candidate.text
                )>.42
            }
            .coerceAtMost(3)

    val ranked=candidates.sortedWith(
        compareByDescending<Candidate>{
            it.relevance +
                (it.quality/12).toInt() +
                support(it)*4
        }
            .thenByDescending{it.quality}
    )
    val fallbackWhat=eventSummary
        .split(Regex("(?<=[.!?])\\s+"))
        .map(::cleanResearchText)
        .filter{it.length in 45..360 && isUsefulResearchText(it)}
        .take(2)

    val titleFallback=articles
        .map{cleanResearchText(it.title)}
        .filter{it.length>=25 && looksTurkish(it)}
        .filter{title->
            val terms=title.lowercase(locale)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter{it.length>3}
                .toSet()
            terms.intersect(queryTerms).isNotEmpty()
        }
        .distinct()
        .take(2)

    val what=distinctTake(ranked,2)
        .ifEmpty{fallbackWhat}
        .ifEmpty{titleFallback}

    val whyCandidates=candidates.filter{it.impact}.sortedWith(
        compareByDescending<Candidate>{
            it.relevance +
                (it.quality/10).toInt() +
                support(it)*3
        }
            .thenByDescending{it.quality}
    )
    val why=distinctTake(whyCandidates,2,what.toSet())

    val latestCandidates=candidates
        .filter{it.latest || it.publishedAt!=null}
        .sortedWith(
            compareByDescending<Candidate>{it.latest}
                .thenByDescending{it.publishedAt?:0L}
                .thenByDescending{
                    it.relevance+support(it)*2
                }
        )
    val used=(what+why).toSet()
    val latest=distinctTake(latestCandidates,2,used)

    return ResearchSummary(whatHappened=what,whyImportant=why,latestSituation=latest)
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
