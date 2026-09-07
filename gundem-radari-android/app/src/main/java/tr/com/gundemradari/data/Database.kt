package tr.com.gundemradari.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sources") data class SourceEntity(
    @PrimaryKey val id:String, val name:String, val groupName:String, val endpoint:String,
    val kind:String, val trust:Double, val enabled:Boolean, val staged:Boolean, val note:String)

@Entity(tableName="source_health") data class SourceHealthEntity(
    @PrimaryKey val sourceId:String,
    val consecutiveFailures:Int=0,
    val lastFailureAt:Long?=null,
    val lastSuccessAt:Long?=null,
    val cooldownUntil:Long=0L)

@Entity(tableName="raw_items") data class RawItemEntity(
    @PrimaryKey val url:String, val sourceId:String, val title:String, val summary:String,
    val publishedAt:Long?, val firstSeenAt:Long, val exactHash:String,
    @ColumnInfo(defaultValue="''") val originalTitle:String="",
    @ColumnInfo(defaultValue="''") val originalSummary:String="")

@Entity(tableName="events") data class EventEntity(
    @PrimaryKey val id:String, val title:String, val summary:String, val scope:String,
    val importance:Double, val noise:Double, val verification:Int, val velocity:Double,
    val sourceCount:Int, val firstSeenAt:Long, val updatedAt:Long, val changeNote:String,
    val publishedAt:Long? = null,
    @ColumnInfo(defaultValue="0") val religionPriority:Int=0,
    @ColumnInfo(defaultValue="'general'") val topic:String="general",
    @ColumnInfo(defaultValue="0") val bestContentQuality:Double=0.0)

@Entity(primaryKeys=["eventId","rawUrl"],tableName="event_items")
data class EventItemEntity(val eventId:String,val rawUrl:String)

@Entity(tableName="event_versions") data class EventVersionEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0, val eventId:String, val version:Int,
    val title:String, val summary:String, val changeNote:String, val createdAt:Long)

@Entity(tableName="scan_history") data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0, val startedAt:Long, val finishedAt:Long?,
    val newItems:Int, val failedSources:Int)

data class ResearchItemRow(
    val sourceName:String, val groupName:String, val url:String, val title:String,
    val summary:String, val originalTitle:String, val originalSummary:String,
    val publishedAt:Long?, val firstSeenAt:Long, val trust:Double)

data class ClassificationItemRow(
    val sourceId:String, val groupName:String, val title:String, val summary:String)

@Dao interface GundemDao {
    @Query("SELECT * FROM sources ORDER BY groupName,name")
    fun sources():Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE enabled=1 AND staged=0")
    suspend fun enabledSources():List<SourceEntity>

    @Query("""
        SELECT s.* FROM sources s
        LEFT JOIN source_health h ON h.sourceId=s.id
        WHERE s.enabled=1 AND s.staged=0
          AND COALESCE(h.cooldownUntil,0)<=:now
        ORDER BY s.groupName,s.name
    """)
    suspend fun scanSources(now:Long):List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id=:id LIMIT 1")
    suspend fun source(id:String):SourceEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun putSource(row:SourceEntity)

    @Query("DELETE FROM sources WHERE id NOT IN (:ids)")
    suspend fun deleteSourcesExcept(ids:List<String>)

    @Query("UPDATE sources SET enabled=:enabled WHERE id=:id")
    suspend fun setSource(id:String,enabled:Boolean)

    @Query("UPDATE sources SET enabled=CASE WHEN staged=0 AND :mode=1 THEN 1 WHEN :mode=0 THEN 0 ELSE enabled END")
    suspend fun setAll(mode:Int)

    @Query("SELECT * FROM source_health WHERE sourceId=:sourceId LIMIT 1")
    suspend fun sourceHealth(sourceId:String):SourceHealthEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun putSourceHealth(row:SourceHealthEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM raw_items WHERE url=:url)")
    suspend fun rawExists(url:String):Boolean

    @Insert(onConflict=OnConflictStrategy.IGNORE)
    suspend fun addRaw(row:RawItemEntity):Long

    @Query("SELECT * FROM events WHERE updatedAt > :after ORDER BY importance DESC LIMIT 180")
    suspend fun recentEvents(after:Long):List<EventEntity>

    @Query("SELECT * FROM events WHERE id=:id")
    suspend fun event(id:String):EventEntity?

    @Query("UPDATE events SET scope=:scope, topic=:topic WHERE id=:id")
    suspend fun updateEventClassification(id:String,scope:String,topic:String)

    @Query("""
        SELECT r.sourceId AS sourceId, s.groupName AS groupName,
               r.title AS title, r.summary AS summary
        FROM event_items ei
        JOIN raw_items r ON r.url=ei.rawUrl
        JOIN sources s ON s.id=r.sourceId
        WHERE ei.eventId=:eventId
    """)
    suspend fun classificationItems(eventId:String):List<ClassificationItemRow>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM event_items ei
            JOIN raw_items r ON r.url=ei.rawUrl
            WHERE ei.eventId=:eventId AND r.sourceId=:sourceId
        )
    """)
    suspend fun eventHasSource(eventId:String,sourceId:String):Boolean

    @Query("SELECT max(version) FROM event_versions WHERE eventId=:eventId")
    suspend fun maxEventVersion(eventId:String):Int?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun putEvent(row:EventEntity)

    @Insert(onConflict=OnConflictStrategy.IGNORE)
    suspend fun link(row:EventItemEntity)

    @Insert
    suspend fun addVersion(row:EventVersionEntity)

    @Query("""
        SELECT * FROM events WHERE scope='turkey' AND noise < 50
        ORDER BY (
            importance - MIN(30.0, MAX(0.0, ((:now-updatedAt)/3600000.0)*0.55))
        ) DESC, updatedAt DESC LIMIT 70
    """)
    fun turkeyFeed(now:Long):Flow<List<EventEntity>>

    @Query("""
        SELECT * FROM events WHERE scope='world' AND noise < 50
        ORDER BY (
            importance - MIN(30.0, MAX(0.0, ((:now-updatedAt)/3600000.0)*0.55))
        ) DESC, updatedAt DESC LIMIT 70
    """)
    fun worldFeed(now:Long):Flow<List<EventEntity>>

    @Query("""
        SELECT * FROM events
        WHERE scope='religion' AND religionPriority > 0 AND noise < 70
        ORDER BY (
            importance + religionPriority*5
            - MIN(30.0, MAX(0.0, ((:now-updatedAt)/3600000.0)*0.55))
        ) DESC, updatedAt DESC LIMIT 70
    """)
    fun religionFeed(now:Long):Flow<List<EventEntity>>

    @Query("""
        SELECT s.name AS sourceName, s.groupName AS groupName, r.url AS url,
               r.title AS title, r.summary AS summary,
               r.originalTitle AS originalTitle, r.originalSummary AS originalSummary,
               r.publishedAt AS publishedAt, r.firstSeenAt AS firstSeenAt, s.trust AS trust
        FROM event_items ei
        JOIN raw_items r ON r.url=ei.rawUrl
        JOIN sources s ON s.id=r.sourceId
        WHERE ei.eventId=:eventId
        ORDER BY s.trust DESC, COALESCE(r.publishedAt,r.firstSeenAt) DESC
    """)
    suspend fun researchItems(eventId:String):List<ResearchItemRow>

    @Insert suspend fun scan(row:ScanHistoryEntity)
    @Query("SELECT max(finishedAt) FROM scan_history")
    fun lastScan():Flow<Long?>
}

@Database(
    entities=[
        SourceEntity::class,SourceHealthEntity::class,RawItemEntity::class,EventEntity::class,
        EventItemEntity::class,EventVersionEntity::class,ScanHistoryEntity::class
    ],
    version=9, exportSchema=false
)
abstract class AppDatabase:RoomDatabase(){
    abstract fun dao():GundemDao
    companion object{
        @Volatile private var INSTANCE:AppDatabase?=null
        private val MIGRATION_1_2=object:Migration(1,2){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE events ADD COLUMN publishedAt INTEGER")}
        }
        private val MIGRATION_2_3=object:Migration(2,3){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")}
        }
        private val MIGRATION_3_4=object:Migration(3,4){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE events ADD COLUMN religionPriority INTEGER NOT NULL DEFAULT 0");db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")}
        }
        private val MIGRATION_4_5=object:Migration(4,5){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE raw_items ADD COLUMN originalTitle TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE raw_items ADD COLUMN originalSummary TEXT NOT NULL DEFAULT ''");db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")}
        }
        private val MIGRATION_5_6=object:Migration(5,6){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")}
        }
        private val MIGRATION_6_7=object:Migration(6,7){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")}
        }
        private val MIGRATION_7_8=object:Migration(7,8){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")}
        }
        private val MIGRATION_8_9=object:Migration(8,9){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE events ADD COLUMN topic TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE events ADD COLUMN bestContentQuality REAL NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS source_health (sourceId TEXT NOT NULL, consecutiveFailures INTEGER NOT NULL, lastFailureAt INTEGER, lastSuccessAt INTEGER, cooldownUntil INTEGER NOT NULL, PRIMARY KEY(sourceId))")
                db.execSQL("DELETE FROM event_items");db.execSQL("DELETE FROM event_versions");db.execSQL("DELETE FROM events");db.execSQL("DELETE FROM raw_items")
            }
        }
        fun get(context:Context)=INSTANCE?:synchronized(this){
            INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"gundem-radari.db")
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9)
                .build().also{INSTANCE=it}
        }
    }
}
