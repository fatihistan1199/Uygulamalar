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
}
