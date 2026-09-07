package tr.com.gundemradari.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sources") data class SourceEntity(
    @PrimaryKey val id:String, val name:String, val groupName:String, val endpoint:String,
    val kind:String, val trust:Double, val enabled:Boolean, val staged:Boolean, val note:String)
@Entity(tableName="raw_items") data class RawItemEntity(
    @PrimaryKey val url:String, val sourceId:String, val title:String, val summary:String,
    val publishedAt:Long?, val firstSeenAt:Long, val exactHash:String)
@Entity(tableName="events") data class EventEntity(
    @PrimaryKey val id:String, val title:String, val summary:String, val scope:String,
    val importance:Double, val noise:Double, val verification:Int, val velocity:Double,
    val sourceCount:Int, val firstSeenAt:Long, val updatedAt:Long, val changeNote:String,
    val publishedAt:Long? = null)
@Entity(primaryKeys=["eventId","rawUrl"],tableName="event_items") data class EventItemEntity(val eventId:String,val rawUrl:String)
@Entity(tableName="event_versions") data class EventVersionEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0, val eventId:String, val version:Int,
    val title:String, val summary:String, val changeNote:String, val createdAt:Long)
@Entity(tableName="scan_history") data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0, val startedAt:Long, val finishedAt:Long?, val newItems:Int, val failedSources:Int)

@Dao interface GundemDao {
    @Query("SELECT * FROM sources ORDER BY groupName,name") fun sources(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources WHERE enabled=1 AND staged=0") suspend fun enabledSources(): List<SourceEntity>
    @Query("SELECT * FROM sources WHERE id=:id LIMIT 1") suspend fun source(id:String):SourceEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putSource(row:SourceEntity)
    @Query("UPDATE sources SET enabled=:enabled WHERE id=:id") suspend fun setSource(id:String,enabled:Boolean)
    @Query("UPDATE sources SET enabled=CASE WHEN staged=0 AND :mode=1 THEN 1 WHEN :mode=0 THEN 0 ELSE enabled END") suspend fun setAll(mode:Int)
    @Query("SELECT EXISTS(SELECT 1 FROM raw_items WHERE url=:url)") suspend fun rawExists(url:String):Boolean
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun addRaw(row:RawItemEntity):Long
    @Query("SELECT * FROM events WHERE updatedAt > :after ORDER BY importance DESC LIMIT 120") suspend fun recentEvents(after:Long):List<EventEntity>
    @Query("SELECT * FROM events WHERE id=:id") suspend fun event(id:String):EventEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putEvent(row:EventEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun link(row:EventItemEntity)
    @Insert suspend fun addVersion(row:EventVersionEntity)
    @Query("SELECT * FROM events WHERE noise < 50 ORDER BY importance DESC,updatedAt DESC LIMIT 60") fun mainFeed():Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE scope='turkey' AND noise < 50 ORDER BY importance DESC,updatedAt DESC LIMIT 60") fun turkeyFeed():Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE scope='world' AND noise < 50 ORDER BY importance DESC,updatedAt DESC LIMIT 60") fun worldFeed():Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE importance >= 55 AND firstSeenAt > :after AND noise < 50 ORDER BY importance DESC") fun missedFeed(after:Long):Flow<List<EventEntity>>
    @Insert suspend fun scan(row:ScanHistoryEntity)
    @Query("SELECT max(finishedAt) FROM scan_history") fun lastScan():Flow<Long?>
}

@Database(
    entities=[SourceEntity::class,RawItemEntity::class,EventEntity::class,EventItemEntity::class,EventVersionEntity::class,ScanHistoryEntity::class],
    version=2,
    exportSchema=false
)
abstract class AppDatabase:RoomDatabase(){
    abstract fun dao():GundemDao
    companion object {
        @Volatile private var INSTANCE:AppDatabase?=null
        private val MIGRATION_1_2=object:Migration(1,2){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE events ADD COLUMN publishedAt INTEGER")
            }
        }
        fun get(context:Context)=INSTANCE?: synchronized(this){
            INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"gundem-radari.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also{INSTANCE=it}
        }
    }
}
