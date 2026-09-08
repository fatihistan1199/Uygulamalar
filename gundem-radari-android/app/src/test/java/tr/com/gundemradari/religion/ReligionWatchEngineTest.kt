package tr.com.gundemradari.religion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.com.gundemradari.data.SourceEntity

class ReligionWatchEngineTest {
    private val diyanet=SourceEntity(
        id="diyanet_haber",
        name="Diyanet Haber",
        groupName="religion_direct",
        endpoint="",
        kind="rss",
        trust=0.90,
        enabled=true,
        staged=false,
        note=""
    )

    private val youtube=diyanet.copy(
        id="religion_people_youtube",
        name="YouTube",
        groupName="religion_search",
        kind="religion_youtube"
    )

    @Test
    fun unrelatedDiyanetStoryIsRejected(){
        val match=ReligionWatchEngine.evaluate(
            diyanet,
            "Cuma hutbesi yayımlandı",
            "Haftanın hutbe konusu açıklandı."
        )
        assertFalse(match.accepted)
    }

    @Test
    fun personInTitleIsAccepted(){
        val match=ReligionWatchEngine.evaluate(
            diyanet,
            "Mehmet Okuyan'dan yeni açıklama",
            "Okuyan konuya ilişkin değerlendirmelerde bulundu."
        )
        assertTrue(match.accepted)
    }

    @Test
    fun incidentalBodyMentionWithoutRelationIsRejected(){
        val match=ReligionWatchEngine.evaluate(
            diyanet,
            "Yeni eğitim programı başladı",
            "Programa çok sayıda isim katıldı. Mehmet Okuyan ismi de listede yer aldı."
        )
        assertFalse(match.accepted)
    }

    @Test
    fun bodyMentionWithClearRelationIsAccepted(){
        val match=ReligionWatchEngine.evaluate(
            diyanet,
            "Tartışılan konuya ilişkin değerlendirme",
            "Mehmet Okuyan konuya ilişkin açıklamasında görüşünü paylaştı."
        )
        assertTrue(match.accepted)
    }

    @Test
    fun youtubeRequiresNameInTitle(){
        val match=ReligionWatchEngine.evaluate(
            youtube,
            "İslam'da ahlak üzerine sohbet",
            "YouTube · Mehmet Okuyan"
        )
        assertFalse(match.accepted)
    }
}
