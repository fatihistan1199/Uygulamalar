package tr.com.gundemradari.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.web.webSourceQuality

class SearchMergeAndSourceQualityTest {

    private fun event(
        id:String,
        title:String,
        summary:String,
        publishedAt:Long=1_000_000L,
        quality:Double=60.0
    )=EventEntity(
        id=id,
        title=title,
        summary=summary,
        scope="search",
        importance=55.0,
        noise=0.0,
        verification=0,
        velocity=0.0,
        sourceCount=1,
        firstSeenAt=publishedAt,
        updatedAt=publishedAt,
        changeNote="",
        publishedAt=publishedAt,
        religionPriority=0,
        topic="search",
        bestContentQuality=quality
    )

    @Test
    fun sameShipAccidentDifferentHeadlinesMerge(){
        val a=event(
            "a",
            "KKTC'deki gemi kazasında can kaybı 14'e yükseldi",
            "Girne önlerinde batan gemide arama kurtarma çalışmaları sürüyor."
        )
        val b=event(
            "b",
            "Girne açıklarında batan gemide ölü sayısı 14 oldu",
            "KKTC açıklarındaki kazada kayıp yolcular için arama kurtarma devam ediyor."
        )

        assertTrue(likelySameSearchEvent(a,b))
    }

    @Test
    fun differentWildfiresDoNotMerge(){
        val a=event(
            "a",
            "Antalya'da orman yangını: Çok sayıda ev tahliye edildi",
            "Aksu ilçesindeki alevler yerleşim yerlerine ulaştı."
        )
        val b=event(
            "b",
            "Muğla'daki orman yangınında alevler Menteşe'den Ula'ya ulaştı",
            "Yaraş Mahallesi'nde başlayan yangına müdahale sürüyor."
        )

        assertFalse(likelySameSearchEvent(a,b))
    }

    @Test
    fun staleSimilarHeadlinesDoNotMerge(){
        val day=24L*60*60*1000
        val a=event(
            "a",
            "Merkez Bankası faiz kararını açıkladı",
            "Politika faizi sabit tutuldu.",
            publishedAt=1_000_000L
        )
        val b=event(
            "b",
            "Merkez Bankası faiz kararını açıkladı",
            "Politika faizi indirildi.",
            publishedAt=1_000_000L+10*day
        )

        assertFalse(likelySameSearchEvent(a,b))
    }

    @Test
    fun authoritativeWebSourcesRankAboveUnknownAndSocial(){
        val trt=webSourceQuality("TRT Haber","https://www.trthaber.com/")
        val unknown=webSourceQuality("Yerel Haber Portalı","https://example.com/")
        val youtube=webSourceQuality("YouTube","https://www.youtube.com/watch?v=x")

        assertTrue(trt>unknown)
        assertTrue(unknown>youtube)
    }

    @Test
    fun publisherFamiliesDoNotDoubleCountSections(){
        assertTrue(sourceFamily("trt_turkiye")==sourceFamily("trt_dunya"))
        assertTrue(sourceFamily("haberturk")==sourceFamily("haberturk_dunya"))
        assertTrue(sourceFamily("sozcu")==sourceFamily("sozcu_dunya"))
    }
    @Test
    fun risingDeathCountDoesNotBecomeFlood(){
        val ship=event(
            "a",
            "Gemi kazasında can kaybı 14'e yükseldi",
            "Arama kurtarma çalışmaları sürüyor."
        )
        val other=event(
            "b",
            "Batan gemide ölü sayısı 14 oldu",
            "Kazanın ardından arama kurtarma devam ediyor."
        )

        assertTrue(likelySameSearchEvent(ship,other))
    }

}
