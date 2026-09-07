package tr.com.gundemradari.scan

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class NewsTranslator {
    private val translator=Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )
    @Volatile private var ready=false

    private fun needsTranslation(item:FetchedItem):Boolean =
        item.source.groupName=="world" || item.source.id in setOf("gdacs","usgs","who")

    suspend fun translateIfNeeded(item:FetchedItem):FetchedItem{
        if(!needsTranslation(item)) return item
        return runCatching{
            if(!ready){
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                ready=true
            }
            val title=translator.translate(item.title).await().trim().ifBlank{item.title}
            val summary=if(item.summary.isBlank()) "" else translator.translate(item.summary).await().trim().ifBlank{item.summary}
            item.copy(title=title,summary=summary)
        }.getOrElse{item}
    }
}
