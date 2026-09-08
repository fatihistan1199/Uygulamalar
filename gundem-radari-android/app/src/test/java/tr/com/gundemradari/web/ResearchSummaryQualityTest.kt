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

}
