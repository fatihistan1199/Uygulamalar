package tr.com.gundemradari.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SourceRepository(private val context:Context,private val dao:GundemDao){
    @Serializable private data class SourceJson(
        val id:String,val name:String,val group:String,val endpoint:String,val kind:String,
        val trust:Double,val enabled:Boolean,val staged:Boolean,val note:String)

    suspend fun seed(){
        val text=context.assets.open("sources.json").bufferedReader().use{it.readText()}
        val rows=Json.decodeFromString<List<SourceJson>>(text)
        rows.forEach{src->
            val row=SourceEntity(src.id,src.name,src.group,src.endpoint,src.kind,src.trust,src.enabled,src.staged,src.note)
            val old=dao.source(src.id)
            val enabled=when{
                old==null -> row.enabled
                old.staged && !row.staged -> row.enabled
                else -> old.enabled
            }
            dao.putSource(row.copy(enabled=enabled))
        }
    }
}
