package tr.com.gundemradari.religion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReligionTopicFilterTest {

    @Test
    fun unrelatedGeneralNewsAreRejected(){
        assertFalse(
            ReligionTitleKeywordFilter.matches(
                "Trabzon'da heyelan: 1 evin çatısı yıkıldı, 2 ev tahliye edildi"
            )
        )
        assertFalse(
            ReligionTitleKeywordFilter.matches(
                "Adana'nın Kozan ilçesinde iki noktada çıkan orman yangınlarına müdahale ediliyor"
            )
        )
        assertFalse(
            ReligionTitleKeywordFilter.matches(
                "Münbiç'te eğitim imkansızlıklarla başladı"
            )
        )
    }

    @Test
    fun tarikatAndCemaatInHeadlineAreAccepted(){
        assertTrue(
            ReligionTitleKeywordFilter.matches(
                "Tarikat yapılanmalarına ilişkin yeni araştırma yayımlandı"
            )
        )
        assertTrue(
            ReligionTitleKeywordFilter.matches(
                "Cemaatlerin eğitim faaliyetleri yeniden gündemde"
            )
        )
    }

    @Test
    fun keywordOnlyInBodyIsIgnored(){
        val title="Yeni araştırmanın sonuçları açıklandı"
        val body="Araştırmada tarikat ve cemaat yapıları da ele alındı."

        assertFalse(ReligionTitleKeywordFilter.matches(title))
        assertTrue(body.contains("tarikat"))
        assertTrue(body.contains("cemaat"))
    }

    @Test
    fun suffixFormsInHeadlineAreAccepted(){
        assertTrue(
            ReligionTitleKeywordFilter.matches(
                "Tarikatların finansman yapısı tartışılıyor"
            )
        )
        assertTrue(
            ReligionTitleKeywordFilter.matches(
                "Cemaatlere yönelik yeni düzenleme"
            )
        )
    }
}
