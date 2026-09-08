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
}
