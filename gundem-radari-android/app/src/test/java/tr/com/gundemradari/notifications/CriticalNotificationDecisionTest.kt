package tr.com.gundemradari.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.com.gundemradari.data.EventEntity

class CriticalNotificationDecisionTest {
    private fun event(
        importance:Double=95.0,
        noise:Double=0.0,
        scope:String="turkey",
        firstSeenAt:Long=1_000_000L,
        updatedAt:Long=1_000_000L,
        publishedAt:Long?=1_000_000L
    )=EventEntity(
        id="event",
        title="Kritik olay",
        summary="Önemli gelişme",
        scope=scope,
        importance=importance,
        noise=noise,
        verification=0,
        velocity=0.0,
        sourceCount=1,
        firstSeenAt=firstSeenAt,
        updatedAt=updatedAt,
        changeNote="İlk kayıt",
        publishedAt=publishedAt,
        religionPriority=0,
        topic="general",
        bestContentQuality=80.0
    )

    @Test
    fun newCriticalEventNotifies(){
        val scanStart=1_000_000L
        val decision=criticalNotificationDecision(
            event=event(),
            scanStartedAt=scanStart,
            now=scanStart+60_000,
            previousNotifiedAt=null,
            previousImportance=null
        )
        assertTrue(decision.shouldNotify)
        assertFalse(decision.isUpdate)
    }

    @Test
    fun oldBacklogDoesNotNotify(){
        val scanStart=10_000_000L
        val decision=criticalNotificationDecision(
            event=event(
                firstSeenAt=scanStart-24L*60*60*1000,
                updatedAt=scanStart-24L*60*60*1000,
                publishedAt=scanStart-24L*60*60*1000
            ),
            scanStartedAt=scanStart,
            now=scanStart+60_000,
            previousNotifiedAt=null,
            previousImportance=null
        )
        assertFalse(decision.shouldNotify)
    }

    @Test
    fun religionDoesNotTriggerCriticalNotification(){
        val scanStart=1_000_000L
        val decision=criticalNotificationDecision(
            event=event(scope="religion"),
            scanStartedAt=scanStart,
            now=scanStart+60_000,
            previousNotifiedAt=null,
            previousImportance=null
        )
        assertFalse(decision.shouldNotify)
    }

    @Test
    fun repeatedNotificationRequiresMaterialImportanceIncrease(){
        val scanStart=10_000_000L
        val decision=criticalNotificationDecision(
            event=event(
                importance=99.0,
                firstSeenAt=scanStart-60_000,
                updatedAt=scanStart
            ),
            scanStartedAt=scanStart,
            now=scanStart+3L*60*60*1000,
            previousNotifiedAt=scanStart-3L*60*60*1000,
            previousImportance=93.0
        )
        assertFalse(decision.shouldNotify)

        val stronger=criticalNotificationDecision(
            event=event(
                importance=100.0,
                firstSeenAt=scanStart-60_000,
                updatedAt=scanStart
            ),
            scanStartedAt=scanStart,
            now=scanStart+3L*60*60*1000,
            previousNotifiedAt=scanStart-3L*60*60*1000,
            previousImportance=92.0
        )
        assertTrue(stronger.shouldNotify)
        assertTrue(stronger.isUpdate)
    }
}
