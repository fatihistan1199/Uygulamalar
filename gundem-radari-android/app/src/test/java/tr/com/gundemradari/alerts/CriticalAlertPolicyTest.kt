package tr.com.gundemradari.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.com.gundemradari.data.EventEntity

class CriticalAlertPolicyTest {
    private val now=1_000_000_000L

    private fun event(
        scope:String="turkey",
        topic:String="disaster",
        importance:Double=90.0,
        noise:Double=0.0,
        title:String="Kritik gelişme",
        updatedAt:Long=now
    )=EventEntity(
        id="test",
        title=title,
        summary="",
        scope=scope,
        importance=importance,
        noise=noise,
        verification=0,
        velocity=0.0,
        sourceCount=1,
        firstSeenAt=updatedAt,
        updatedAt=updatedAt,
        changeNote="",
        publishedAt=updatedAt,
        religionPriority=0,
        topic=topic,
        bestContentQuality=70.0
    )

    @Test
    fun majorTurkeyDisasterCanAlert(){
        assertTrue(
            CriticalAlertPolicy.evaluate(
                event(
                    topic="disaster",
                    importance=91.0,
                    title="Büyük depremde ağır yıkım ve tahliyeler"
                ),
                now
            ).eligible
        )
    }

    @Test
    fun routineEarthquakeSearchNeverAlerts(){
        assertFalse(
            CriticalAlertPolicy.evaluate(
                event(
                    topic="disaster",
                    importance=99.0,
                    title="Deprem mi oldu? Son depremler"
                ),
                now
            ).eligible
        )
    }

    @Test
    fun worldThresholdIsStricter(){
        assertFalse(
            CriticalAlertPolicy.evaluate(
                event(
                    scope="world",
                    topic="disaster",
                    importance=90.0
                ),
                now
            ).eligible
        )

        assertTrue(
            CriticalAlertPolicy.evaluate(
                event(
                    scope="world",
                    topic="security",
                    importance=96.0,
                    title="Geniş çaplı savaş saldırısı"
                ),
                now
            ).eligible
        )
    }

    @Test
    fun staleCriticalEventDoesNotAlert(){
        assertFalse(
            CriticalAlertPolicy.evaluate(
                event(
                    importance=99.0,
                    updatedAt=now-9L*3_600_000
                ),
                now
            ).eligible
        )
    }

    @Test
    fun religionFeedDoesNotGenerateCriticalAlert(){
        assertFalse(
            CriticalAlertPolicy.evaluate(
                event(
                    scope="religion",
                    topic="religion",
                    importance=100.0
                ),
                now
            ).eligible
        )
    }
}
