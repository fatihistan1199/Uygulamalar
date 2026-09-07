package tr.com.gundemradari.alerts

import tr.com.gundemradari.data.EventEntity
import java.util.Locale

data class CriticalAlertDecision(
    val eligible:Boolean,
    val urgency:Int=0,
    val reason:String=""
)

object CriticalAlertPolicy {
    private val locale=Locale("tr","TR")

    private val blockedNoiseTerms=listOf(
        "deprem mi oldu",
        "son depremler",
        "hangi illerde hissedildi",
        "kimdir",
        "kaç yaşında",
        "canlı izle",
        "maç sonucu",
        "magazin"
    )

    private val turkeyCriticalTopics=setOf(
        "disaster",
        "security",
        "politics",
        "economy",
        "health"
    )

    private val worldCriticalTopics=setOf(
        "disaster",
        "security"
    )

    fun evaluate(
        event:EventEntity,
        now:Long=System.currentTimeMillis()
    ):CriticalAlertDecision{
        if(event.scope!="turkey"&&event.scope!="world"){
            return CriticalAlertDecision(false,reason="bildirim kapsamı dışında")
        }

        if(event.noise>=45.0){
            return CriticalAlertDecision(false,reason="gürültü puanı yüksek")
        }

        val text=(event.title+" "+event.summary).lowercase(locale)
        if(blockedNoiseTerms.any{text.contains(it)}){
            return CriticalAlertDecision(false,reason="rutin/gürültü içerik")
        }

        val freshest=maxOf(
            event.publishedAt?:event.firstSeenAt,
            event.updatedAt
        )

        val ageHours=((now-freshest).coerceAtLeast(0L))/3_600_000.0
        if(ageHours>8.0){
            return CriticalAlertDecision(false,reason="kritik bildirim için eski")
        }

        val threshold=when(event.scope){
            "turkey"->if(event.topic in turkeyCriticalTopics)88.0 else 94.0
            "world"->if(event.topic in worldCriticalTopics)94.0 else 98.0
            else->100.0
        }

        if(event.importance<threshold){
            return CriticalAlertDecision(false,reason="önem eşiğinin altında")
        }

        val urgency=when{
            event.importance>=97.0->3
            event.importance>=93.0->2
            else->1
        }

        return CriticalAlertDecision(
            eligible=true,
            urgency=urgency,
            reason="kritik ve güncel olay"
        )
    }
}
