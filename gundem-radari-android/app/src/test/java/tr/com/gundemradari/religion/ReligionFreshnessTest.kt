package tr.com.gundemradari.religion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReligionFreshnessTest {

    @Test
    fun unknownDateIsRejected(){
        assertFalse(ReligionTracker.isFresh(null,1_800_000_000_000L))
    }

    @Test
    fun itemOlderThanThirtyDaysIsRejected(){
        val now=1_800_000_000_000L
        val tooOld=now-(31L*24*60*60*1000)
        assertFalse(ReligionTracker.isFresh(tooOld,now))
    }

    @Test
    fun recentItemIsAccepted(){
        val now=1_800_000_000_000L
        val recent=now-(10L*24*60*60*1000)
        assertTrue(ReligionTracker.isFresh(recent,now))
    }

    @Test
    fun trackedPersonResolvesSocialProfile(){
        val profile=ReligionTracker.profileFor(
            "Mehmet Okuyan yeni açıklama yaptı",
            ""
        )
        assertNotNull(profile)
        assertTrue(profile!!.xUrl.contains("okuyanmehmet"))
        assertTrue(profile.instagramUrl.contains("okuyanmehmet"))
    }

    @Test
    fun screenshotOldDatesAreRejectedInSeptember2026(){
        val now=java.time.ZonedDateTime
            .of(2026,9,9,0,30,0,0,java.time.ZoneId.of("Europe/Istanbul"))
            .toInstant()
            .toEpochMilli()

        val november2022=java.time.ZonedDateTime
            .of(2022,11,7,11,0,0,0,java.time.ZoneId.of("Europe/Istanbul"))
            .toInstant()
            .toEpochMilli()

        val march2026=java.time.ZonedDateTime
            .of(2026,3,4,11,0,0,0,java.time.ZoneId.of("Europe/Istanbul"))
            .toInstant()
            .toEpochMilli()

        assertFalse(ReligionTracker.isFresh(november2022,now))
        assertFalse(ReligionTracker.isFresh(march2026,now))
    }

}
