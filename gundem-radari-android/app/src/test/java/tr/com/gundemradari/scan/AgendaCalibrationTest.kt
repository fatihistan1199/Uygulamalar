package tr.com.gundemradari.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.com.gundemradari.data.ClassificationItemRow
import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.data.SourceEntity

class AgendaCalibrationTest {
    private val trtTurkey=SourceEntity(
        id="trt_turkiye",
        name="TRT Haber Türkiye",
        groupName="turkey",
        endpoint="",
        kind="rss",
        trust=0.82,
        enabled=true,
        staged=false,
        note=""
    )

    private fun event(
        title:String,
        summary:String=""
    )=EventEntity(
        id="test",
        title=title,
        summary=summary,
        scope="world",
        importance=50.0,
        noise=0.0,
        verification=0,
        velocity=0.0,
        sourceCount=1,
        firstSeenAt=1L,
        updatedAt=1L,
        changeNote="",
        publishedAt=1L,
        religionPriority=0,
        topic="general",
        bestContentQuality=50.0
    )

    @Test
    fun massCasualtyTransportRanksAboveRoutineAgenda(){
        val ship=scores(
            title="KKTC'deki gemi kazasında can kaybı 14'e yükseldi",
            source=trtTurkey,
            summary="Girne önlerinde batan gemide arama kurtarma çalışmaları sürüyor."
        ).first

        val routine=scores(
            title="Okullarda uyum haftası başladı",
            source=trtTurkey,
            summary="Öğrenciler için uyum programı başladı."
        ).first

        assertTrue(ship>=75.0)
        assertTrue(ship>routine+35.0)
    }

    @Test
    fun wildfireWithEvacuationIsPromoted(){
        val score=scores(
            title="Antalya'da orman yangını: Çok sayıda ev tahliye edildi",
            source=trtTurkey,
            summary="Alevler yerleşim yerlerine ulaştı ve çok sayıda ev tahliye edildi."
        ).first

        assertTrue(score>=68.0)
    }

    @Test
    fun trivialSmallEarthquakeIsSuppressed(){
        val result=scores(
            title="Deprem mi oldu? 4,2 büyüklüğünde deprem",
            source=trtTurkey,
            summary="Son depremler listesi ve hangi illerde hissedildiği araştırılıyor."
        )

        assertTrue(result.first<35.0)
        assertTrue(result.second>=50.0)
    }

    @Test
    fun generalElectionIsCritical(){
        val score=scores(
            title="Türkiye genel seçim sonuçları açıklandı",
            source=trtTurkey,
            summary="Oy sayımı ülke genelinde tamamlandı."
        ).first

        assertTrue(score>=90.0)
    }

    @Test
    fun indonesiaVolcanoRemainsWorldEvenOnTurkeyFeed(){
        val e=event(
            title="Endonezya'daki yanardağ patlaması uçuşları iptal ettirdi",
            summary="Bali çevresindeki volkanik faaliyet nedeniyle uçuşlar iptal edildi."
        )

        val decision=EventClassifier.classify(
            e,
            listOf(
                ClassificationItemRow(
                    sourceId=trtTurkey.id,
                    groupName=trtTurkey.groupName,
                    title=e.title,
                    summary=e.summary
                )
            )
        )

        assertEquals("world",decision.scope)
        assertEquals("disaster",decision.topic)
    }

    @Test
    fun cabinetDecisionIsTurkeyPolitics(){
        val e=event(
            title="Cumhurbaşkanlığı Kabinesi toplandı",
            summary="Kabine toplantısında Türkiye gündemine ilişkin kararlar ele alındı."
        )

        val decision=EventClassifier.classify(
            e,
            listOf(
                ClassificationItemRow(
                    sourceId=trtTurkey.id,
                    groupName=trtTurkey.groupName,
                    title=e.title,
                    summary=e.summary
                )
            )
        )

        assertEquals("turkey",decision.scope)
        assertEquals("politics",decision.topic)
    }
}
