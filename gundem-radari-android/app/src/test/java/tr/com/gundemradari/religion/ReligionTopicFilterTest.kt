package tr.com.gundemradari.religion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReligionTopicFilterTest {

    @Test
    fun screenshotGeneralNewsAreRejected(){
        assertFalse(
            ReligionTopicFilter.isRelevant(
                "Trabzon'da heyelan: 1 evin çatısı yıkıldı, 2 ev tahliye edildi",
                "Heyelan nedeniyle bir evde hasar oluştu, 2 ev tedbir amacıyla boşaltıldı."
            )
        )

        assertFalse(
            ReligionTopicFilter.isRelevant(
                "Adana'nın Kozan ilçesinde iki noktada çıkan orman yangınlarına müdahale ediliyor",
                "Yangınlar havadan ve karadan müdahaleyle kontrol altına alınmaya çalışılıyor."
            )
        )

        assertFalse(
            ReligionTopicFilter.isRelevant(
                "Münbiç'te eğitim imkansızlıklarla başladı",
                "Savaş mağduru çocuklar tüm imkansızlıklara rağmen eğitimlerine başladı."
            )
        )
    }

    @Test
    fun clearlyReligiousNewsAreAccepted(){
        assertTrue(
            ReligionTopicFilter.isRelevant(
                "Diyanet cuma hutbesinin konusunu açıkladı",
                "Hutbede kul hakkı, ibadet ve ahlak konuları ele alınacak."
            )
        )
        assertTrue(
            ReligionTopicFilter.isRelevant(
                "Kur'an kurslarında yeni dönem başladı",
                "Hafızlık eğitimi ve dini eğitim programları devam ediyor."
            )
        )
    }

    @Test
    fun oneGenericSupportingWordIsNotEnough(){
        assertFalse(
            ReligionTopicFilter.isRelevant(
                "Manevi destek toplantısı yapıldı",
                "Etkinlikte eğitim ve sosyal yardım konuları konuşuldu."
            )
        )
    }
}
