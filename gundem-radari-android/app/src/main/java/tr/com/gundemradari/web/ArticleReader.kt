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
    val details:List<String>,
    val background:List<String>
)

class ArticleReader {
    suspend fun read(url:String):ArticleDetails?=withContext(Dispatchers.IO){
        runCatching{
            val first=Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) GundemRadari/0.6")
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
                            .userAgent("Mozilla/5.0 (Android) GundemRadari/0.6")
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
            ).firstOrNull{it.length>=45}.orEmpty().replace(Regex("\\s+")," ").trim()

            val selectors=listOf(
                "article p",
                "[itemprop=articleBody] p",
                ".article-body p",
                ".article-content p",
                ".news-content p",
                ".story-body p",
                ".content-detail p",
                ".detail-content p",
                "main p"
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

    private fun usableParagraph(text:String):Boolean{
        if(text.length !in 50..1100)return false
        val lower=text.lowercase(Locale("tr","TR"))
        val blocked=listOf(
            "çerez","cookie","reklam","abonelik","abone ol","bildirimleri aç","tüm hakları saklıdır",
            "kişisel veriler","gizlilik politikası","üyelik","giriş yap","uygulamamızı indir"
        )
        return blocked.none{lower.contains(it)}
    }
}

fun summarizeResearch(eventTitle:String,articles:List<ResearchedArticle>):ResearchSummary{
    val locale=Locale("tr","TR")
    val queryTerms=eventTitle.lowercase(locale)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter{it.length>3}
        .toSet()

    data class Candidate(
        val text:String,
        val score:Int,
        val primary:Boolean,
        val background:Boolean
    )

    val backgroundMarkers=listOf(
        "daha önce","geçen yıl","geçen ay","geçen hafta","önceki","süreç","başlamıştı",
        "başladı","ardından","sonrasında","yıllardır","uzun süredir","tarihinde","bu yana",
        "geçmişte","ilk kez","daha önceki"
    )

    val candidates=mutableListOf<Candidate>()
    articles.forEachIndexed{articleIndex,article->
        val blocks=buildList{
            if(article.description.isNotBlank())add(article.description)
            addAll(article.paragraphs.take(14))
        }
        blocks.forEachIndexed{idx,block->
            block.split(Regex("(?<=[.!?])\\s+"))
                .map{it.replace(Regex("\\s+")," ").trim()}
                .filter{it.length in 55..360}
                .forEach{sentence->
                    val lower=sentence.lowercase(locale)
                    val terms=lower.split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter{it.length>3}
                        .toSet()
                    val overlap=terms.intersect(queryTerms).size
                    val hasNumber=sentence.any(Char::isDigit)
                    val hasQuote=sentence.contains("\"")||sentence.contains("“")||sentence.contains("”")
                    val isBackground=backgroundMarkers.any{lower.contains(it)}
                    var score=overlap*7
                    if(articleIndex==0)score+=8
                    if(idx<4)score+=5
                    if(hasNumber)score+=2
                    if(hasQuote)score+=2
                    if(sentence.length in 80..220)score+=2
                    if(isBackground)score+=1
                    candidates+=Candidate(sentence,score,articleIndex==0,isBackground)
                }
        }
    }

    val selected=mutableListOf<Candidate>()
    for(c in candidates.sortedByDescending{it.score}){
        if(selected.none{sentenceSimilarity(it.text,c.text)>.62}){
            selected+=c
            if(selected.size>=14)break
        }
    }

    val primaryLead=selected.filter{it.primary&&!it.background}.take(2)
    val what=(if(primaryLead.isNotEmpty())primaryLead else selected.filter{!it.background}.take(2))
        .map{it.text}

    val background=selected
        .filter{it.background && it.text !in what}
        .take(3)
        .map{it.text}

    val details=selected
        .filter{it.text !in what && it.text !in background}
        .take(6)
        .map{it.text}

    return ResearchSummary(
        whatHappened=what,
        details=details,
        background=background
    )
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
    val isPrimary:Boolean
)
