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

class ArticleReader {
    suspend fun read(url:String):ArticleDetails?=withContext(Dispatchers.IO){
        runCatching{
            val first=Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) GundemRadari/0.5")
                .timeout(12000)
                .followRedirects(true)
                .get()

            val doc=if(first.location().contains("news.google.com")){
                val external=first.select("a[href]").asSequence()
                    .map{it.absUrl("href")}
                    .firstOrNull{href->
                        href.startsWith("http") &&
                        !href.contains("google.com") &&
                        !href.contains("gstatic.com")
                    }
                if(external!=null){
                    runCatching{
                        Jsoup.connect(external)
                            .userAgent("Mozilla/5.0 (Android) GundemRadari/0.5")
                            .timeout(12000)
                            .followRedirects(true)
                            .get()
                    }.getOrElse{first}
                }else first
            }else first

            doc.select("script,style,nav,aside,footer,form,noscript").remove()

            val ogTitle=doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
            val h1=doc.selectFirst("h1")?.text().orEmpty()
            val title=listOf(ogTitle,h1,doc.title()).firstOrNull{it.isNotBlank()}.orEmpty().trim()

            val description=listOf(
                doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
                doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
            ).firstOrNull{it.length>=40}.orEmpty().trim()

            val selectors=listOf(
                "article p",
                "[itemprop=articleBody] p",
                ".article-body p",
                ".article-content p",
                ".news-content p",
                ".story-body p",
                "main p"
            )

            val paras=selectors.asSequence()
                .flatMap{sel->doc.select(sel).asSequence()}
                .map{it.text().replace(Regex("\\s+")," ").trim()}
                .filter{it.length in 45..900}
                .distinct()
                .take(18)
                .toList()
                .ifEmpty{
                    doc.select("body p").asSequence()
                        .map{it.text().replace(Regex("\\s+")," ").trim()}
                        .filter{it.length in 60..900}
                        .distinct()
                        .take(12)
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
}

fun summarizeResearch(eventTitle:String,articles:List<ResearchedArticle>):Pair<List<String>,List<String>>{
    val queryTerms=eventTitle.lowercase(Locale("tr","TR"))
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter{it.length>3}
        .toSet()

    data class Candidate(val text:String,val score:Int)

    val candidates=mutableListOf<Candidate>()
    articles.forEachIndexed{articleIndex,article->
        val parts=buildList{
            if(article.description.isNotBlank())add(article.description)
            addAll(article.paragraphs.take(10))
        }
        parts.forEachIndexed{idx,p->
            p.split(Regex("(?<=[.!?])\\s+"))
                .map{it.trim()}
                .filter{it.length in 50..330}
                .forEach{sentence->
                    val terms=sentence.lowercase(Locale("tr","TR"))
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter{it.length>3}
                        .toSet()
                    val overlap=terms.intersect(queryTerms).size
                    val score=overlap*6 + (if(articleIndex==0)5 else 0) + (if(idx<3)3 else 0)
                    candidates+=Candidate(sentence,score)
                }
        }
    }

    val ordered=candidates.sortedByDescending{it.score}
    val selected=mutableListOf<String>()
    for(c in ordered){
        if(selected.none{sentenceSimilarity(it,c.text)>.62}){
            selected+=c.text
            if(selected.size>=8)break
        }
    }

    val what=selected.take(2)
    val details=selected.drop(2).take(6)
    return what to details
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
