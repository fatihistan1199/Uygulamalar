package tr.com.gundemradari.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchSummaryQualityTest {

    @Test
    fun googleNewsBoilerplateIsRejected(){
        val text="Comprehensive up-to-date news coverage, aggregated from sources all over the world by Google News."
        assertFalse(isUsefulResearchText(text))
    }

    @Test
    fun relevantTurkishSentenceIsAccepted(){
        val text="Cübbeli Ahmet, konuya ilişkin yaptığı açıklamada kararın İslam'a göre caiz olmadığını söyledi."
        assertTrue(isUsefulResearchText(text))
    }

    @Test
    fun summaryPrefersRelevantSentence(){
        val article=ResearchedArticle(
            sourceName="Haber",
            title="Cübbeli Ahmet'ten itiraz geldi",
            url="https://example.com/a",
            description="Cübbeli Ahmet, konuya ilişkin açıklamasında kararın İslam'a göre caiz olmadığını söyledi.",
            paragraphs=listOf(
                "Sitedeki diğer haberlerde ekonomi ve spor gündemine ilişkin farklı başlıklar da yer aldı."
            ),
            publishedAt=1L,
            isPrimary=true,
            quality=0.75
        )

        val summary=summarizeResearch(
            eventTitle="Cübbeli Ahmet'ten itiraz geldi: İslam'a göre de caiz değil",
            eventSummary="",
            articles=listOf(article)
        )

        assertTrue(
            summary.whatHappened.any{
                it.contains("Cübbeli Ahmet")
            }
        )
    }

    @Test
    fun earlyArticleFactDoesNotRequireTitleWordOverlap(){
        val article=ResearchedArticle(
            sourceName="Haber",
            title="Yeni düzenleme duyuruldu",
            url="https://example.com/b",
            description="Düzenlemeyle birlikte başvurular 15 Eylül tarihinde başlayacak ve işlemler e-Devlet üzerinden yürütülecek.",
            paragraphs=listOf(
                "Yetkililer uygulamanın ilk aşamada yaklaşık iki milyon kişiyi kapsamasının beklendiğini bildirdi."
            ),
            publishedAt=2L,
            isPrimary=true,
            quality=85.0
        )

        val summary=summarizeResearch(
            eventTitle="Bakanlıktan vatandaşları ilgilendiren yeni karar",
            eventSummary="",
            articles=listOf(article)
        )

        assertTrue(summary.whatHappened.isNotEmpty())
        assertTrue(
            summary.whatHappened.joinToString(" ").contains("15 Eylül") ||
            summary.whatHappened.joinToString(" ").contains("iki milyon")
        )
    }

    @Test
    fun importanceAnalysisIsProducedWhenArticleHasEconomicContext(){
        val article=ResearchedArticle(
            sourceName="Ekonomi",
            title="Merkez Bankası faiz kararını açıkladı",
            url="https://example.com/c",
            description="Merkez Bankası politika faizini sabit tuttuğunu ve enflasyon görünümünü yakından izlemeyi sürdüreceğini açıkladı.",
            paragraphs=listOf(
                "Kararın ardından piyasalarda döviz ve tahvil fiyatlamaları yakından takip edildi."
            ),
            publishedAt=3L,
            isPrimary=true,
            quality=90.0
        )

        val summary=summarizeResearch(
            eventTitle="Merkez Bankası faiz kararını açıkladı",
            eventSummary="",
            articles=listOf(article)
        )

        assertTrue(summary.whyImportant.isNotEmpty())
    }


    @Test
    fun publisherDescriptionPreventsEmptyWhatHappened(){
        val article=ResearchedArticle(
            sourceName="Doğrudan yayıncı",
            title="İsrafil Balcı yeni açıklamada bulundu",
            url="https://example.com/d",
            description="İlahiyatçı İsrafil Balcı, yaptığı konuşmada tartışılan konuya ilişkin görüşlerini açıkladı ve önceki değerlendirmelerine açıklık getirdi.",
            paragraphs=emptyList(),
            publishedAt=4L,
            isPrimary=true,
            quality=70.0
        )

        val summary=summarizeResearch(
            eventTitle="İsrafil Balcı'dan yeni açıklama",
            eventSummary="",
            articles=listOf(article)
        )

        assertTrue(summary.whatHappened.isNotEmpty())
        assertTrue(summary.whatHappened.first().contains("İsrafil Balcı"))
    }


    @Test
    fun jsonLdArticleBodyIsExtracted(){
        val html="""
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@context":"https://schema.org",
                "@type":"NewsArticle",
                "headline":"Örnek haber başlığı",
                "description":"Örnek olayla ilgili yeterince uzun ve açıklayıcı bir haber özeti burada yer alıyor.",
                "datePublished":"2026-09-08T12:30:00+03:00",
                "articleBody":"Yetkililer olayın sabah saatlerinde başladığını ve ekiplerin bölgeye sevk edildiğini açıkladı. Çalışmaların gün boyunca sürdüğü, ulaşımın ise kontrollü biçimde sağlandığı bildirildi."
              }
              </script>
            </head>
            <body>
              <div class="related-news">
                <p>İlgili haberler bölümünde başka bir spor ve magazin haberi yer alıyor.</p>
              </div>
            </body>
            </html>
        """.trimIndent()

        val details=ArticleReader().extractHtmlForTest(html)

        assertTrue(details.title.contains("Örnek haber başlığı"))
        assertTrue(details.paragraphs.joinToString(" ").contains("Yetkililer olayın"))
        assertFalse(details.paragraphs.joinToString(" ").contains("İlgili haberler"))
        assertTrue(details.publishedAt!=null)
    }

    @Test
    fun densePublisherBodyBeatsPeripheralText(){
        val html="""
            <html>
            <head><title>Test haber</title></head>
            <body>
              <aside>
                <p>Bu kenar alanında başka içeriklere yönlendiren uzun bir tanıtım metni bulunuyor ve haber gövdesi değildir.</p>
                <p>Bu bölümde kullanıcıyı başka sayfalara götüren ikinci bir öneri metni daha yer alıyor.</p>
              </aside>
              <div class="article-content">
                <p>Belediye ekipleri sabah saatlerinde başlayan arızaya müdahale ederek ana hattaki çalışmayı tamamladı.</p>
                <p>Yetkililer, hizmetin kademeli biçimde normale döndüğünü ve ek kontrollerin gün içinde süreceğini açıkladı.</p>
              </div>
            </body>
            </html>
        """.trimIndent()

        val details=ArticleReader().extractHtmlForTest(html)

        val joined=details.paragraphs.joinToString(" ")
        assertTrue(joined.contains("Belediye ekipleri"))
        assertTrue(joined.contains("hizmetin kademeli"))
        assertFalse(joined.contains("kenar alanında"))
    }

    @Test
    fun summarySeparatesEventImpactAndLatestState(){
        val article=ResearchedArticle(
            sourceName="Yayıncı",
            title="Yeni ulaşım düzenlemesi açıklandı",
            url="https://example.com/e",
            description="Büyükşehir Belediyesi, merkez hattında hafta sonu yeni sefer düzenine geçileceğini açıkladı.",
            paragraphs=listOf(
                "Düzenleme, günlük yaklaşık 300 bin yolcunun kullandığı hatta sefer aralıklarını ve aktarma planını değiştirecek.",
                "Belediye, yeni tarifelerin cumartesi sabahı yürürlüğe gireceğini ve ilk hafta ek ekiplerin sahada olacağını bildirdi."
            ),
            publishedAt=10L,
            isPrimary=true,
            quality=90.0
        )

        val summary=summarizeResearch(
            eventTitle="Merkez hattında yeni sefer düzeni",
            eventSummary="",
            articles=listOf(article)
        )

        val what=summary.whatHappened.joinToString(" ")
        val why=summary.whyImportant.joinToString(" ")
        val latest=summary.latestSituation.joinToString(" ")

        assertTrue(what.contains("Belediyesi") || what.contains("sefer düzenine"))
        assertTrue(why.contains("300 bin") || why.contains("değiştirecek"))
        assertTrue(latest.contains("cumartesi") || latest.contains("yürürlüğe"))
        assertTrue(what!=why)
        assertTrue(what!=latest)
    }

    @Test
    fun contextDependentLeadIsPenalized(){
        val article=ResearchedArticle(
            sourceName="Yayıncı",
            title="Yeni karar açıklandı",
            url="https://example.com/f",
            description="Bu nedenle uygulamanın önümüzdeki günlerde yeniden değerlendirilmesi bekleniyor.",
            paragraphs=listOf(
                "Bakanlık, 15 Eylül'den itibaren başvuruların e-Devlet üzerinden alınacağını açıkladı."
            ),
            publishedAt=11L,
            isPrimary=true,
            quality=88.0
        )

        val summary=summarizeResearch(
            eventTitle="Başvurular için yeni karar",
            eventSummary="",
            articles=listOf(article)
        )

        assertTrue(
            summary.whatHappened.firstOrNull()?.contains("Bakanlık")==true ||
            summary.whatHappened.joinToString(" ").contains("15 Eylül")
        )
    }

}
