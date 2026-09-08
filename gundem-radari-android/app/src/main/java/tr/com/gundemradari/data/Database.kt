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

@Entity(
    tableName="raw_items",
    indices=[
        Index(value=["sourceId"]),
        Index(value=["firstSeenAt"])
    ]
) data class RawItemEntity(
    @PrimaryKey val url:String, val sourceId:String, val title:String, val summary:String,
    val publishedAt:Long?, val firstSeenAt:Long, val exactHash:String,
    @ColumnInfo(defaultValue="''") val originalTitle:String="",
    @ColumnInfo(defaultValue="''") val originalSummary:String="")

@Entity(
    tableName="events",
    indices=[
        Index(value=["updatedAt"]),
        Index(value=["scope","noise","updatedAt"])
    ]
) data class EventEntity(
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

@Entity(tableName="event_enrichment") data class EventEnrichmentEntity(
    @PrimaryKey val eventId:String,
    val sourceName:String,
    val sourceUrl:String,
    val sourceTitle:String,
    val shortSummary:String,
    val whatHappened:String,
    val whyImportant:String,
    val latestSituation:String,
    val contentQuality:Double,
    val enrichedAt:Long)

@Entity(tableName="scan_history") data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0, val startedAt:Long, val finishedAt:Long?,
    val newItems:Int, val failedSources:Int)

data class ResearchItemRow(
    val sourceName:String, val groupName:String, val url:String, val title:String,
    val summary:String, val originalTitle:String, val originalSummary:String,
    val publishedAt:Long?, val firstSeenAt:Long, val trust:Double)

data class ClassificationItemRow(
    val sourceId:String, val groupName:String, val title:String, val summary:String)

data class SearchEventRow(
    @Embedded val event:EventEntity,
    val searchText:String
)

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

    @Query("""
        SELECT DISTINCT r.sourceId
        FROM event_items ei
        JOIN raw_items r ON r.url=ei.rawUrl
        WHERE ei.eventId=:eventId
    """)
    suspend fun eventSourceIds(eventId:String):List<String>

    @Query("SELECT max(version) FROM event_versions WHERE eventId=:eventId")
    suspend fun maxEventVersion(eventId:String):Int?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun putEvent(row:EventEntity)

    @Insert(onConflict=OnConflictStrategy.IGNORE)
    suspend fun link(row:EventItemEntity)

    @Insert
    suspend fun addVersion(row:EventVersionEntity)

    @Query("SELECT * FROM event_enrichment WHERE eventId=:eventId LIMIT 1")
    suspend fun enrichment(eventId:String):EventEnrichmentEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun putEnrichment(row:EventEnrichmentEntity)

    @Query("""
        UPDATE events
        SET summary=:summary,
            bestContentQuality=CASE
                WHEN bestContentQuality>:quality THEN bestContentQuality
                ELSE :quality
            END
        WHERE id=:eventId
    """)
    suspend fun updateEnrichedSummary(eventId:String,summary:String,quality:Double)

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
        SELECT e.* FROM events e
        WHERE e.scope='religion'
          AND e.religionPriority > 0
          AND e.noise < 70
          AND EXISTS (
              SELECT 1
              FROM event_items ei
              JOIN raw_items r ON r.url=ei.rawUrl
              WHERE ei.eventId=e.id
                AND r.publishedAt IS NOT NULL
                AND r.publishedAt>=:after
          )
        ORDER BY (
            e.importance + e.religionPriority*5
            - MIN(30.0, MAX(0.0, ((:now-e.updatedAt)/3600000.0)*0.55))
        ) DESC, e.publishedAt DESC, e.updatedAt DESC LIMIT 70
    """)
    fun religionFeed(now:Long,after:Long):Flow<List<EventEntity>>

    @Query("""
        SELECT s.name AS sourceName, s.groupName AS groupName, r.url AS url,
               r.title AS title, r.summary AS summary,
               r.originalTitle AS originalTitle, r.originalSummary AS originalSummary,
               r.publishedAt AS publishedAt, r.firstSeenAt AS firstSeenAt, s.trust AS trust
        FROM event_items ei
        JOIN raw_items r ON r.url=ei.rawUrl
        JOIN sources s ON s.id=r.sourceId
        WHERE ei.eventId=:eventId
          AND r.publishedAt IS NOT NULL
          AND r.publishedAt>=:after
        ORDER BY s.trust DESC, r.publishedAt DESC
    """)
    suspend fun researchItemsFresh(eventId:String,after:Long):List<ResearchItemRow>

    @Query("""
        DELETE FROM event_enrichment
        WHERE eventId IN (
            SELECT e.id FROM events e
            WHERE (e.scope='religion' OR e.religionPriority>0)
              AND NOT EXISTS (
                  SELECT 1
                  FROM event_items ei
                  JOIN raw_items r ON r.url=ei.rawUrl
                  WHERE ei.eventId=e.id
                    AND r.publishedAt IS NOT NULL
                    AND r.publishedAt>=:after
              )
        )
    """)
    suspend fun deleteOldReligionEnrichment(after:Long)

    @Query("""
        DELETE FROM event_versions
        WHERE eventId IN (
            SELECT e.id FROM events e
            WHERE (e.scope='religion' OR e.religionPriority>0)
              AND NOT EXISTS (
                  SELECT 1
                  FROM event_items ei
                  JOIN raw_items r ON r.url=ei.rawUrl
                  WHERE ei.eventId=e.id
                    AND r.publishedAt IS NOT NULL
                    AND r.publishedAt>=:after
              )
        )
    """)
    suspend fun deleteOldReligionVersions(after:Long)

    @Query("""
        DELETE FROM event_items
        WHERE eventId IN (
            SELECT e.id FROM events e
            WHERE (e.scope='religion' OR e.religionPriority>0)
              AND NOT EXISTS (
                  SELECT 1
                  FROM event_items fresh_ei
                  JOIN raw_items fresh_r ON fresh_r.url=fresh_ei.rawUrl
                  WHERE fresh_ei.eventId=e.id
                    AND fresh_r.publishedAt IS NOT NULL
                    AND fresh_r.publishedAt>=:after
              )
        )
    """)
    suspend fun deleteOldReligionLinks(after:Long)

    @Query("""
        DELETE FROM events
        WHERE (scope='religion' OR religionPriority>0)
          AND NOT EXISTS (
              SELECT 1
              FROM event_items ei
              JOIN raw_items r ON r.url=ei.rawUrl
              WHERE ei.eventId=events.id
                AND r.publishedAt IS NOT NULL
                AND r.publishedAt>=:after
          )
    """)
    suspend fun deleteOldReligionEvents(after:Long)

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


    @Query("""
        SELECT e.*,
               (
                   e.title || ' ' || e.summary || ' ' ||
                   COALESCE(GROUP_CONCAT(r.title || ' ' || r.summary,' '),'')
               ) AS searchText
        FROM events e
        LEFT JOIN event_items ei ON ei.eventId=e.id
        LEFT JOIN raw_items r ON r.url=ei.rawUrl
        WHERE e.scope=:scope AND e.noise < 70
        GROUP BY e.id
        ORDER BY (
            e.importance - MIN(30.0, MAX(0.0, ((:now-e.updatedAt)/3600000.0)*0.55))
        ) DESC, e.updatedAt DESC
        LIMIT 120
    """)
    suspend fun searchEvents(scope:String,now:Long):List<SearchEventRow>

    @Insert suspend fun scan(row:ScanHistoryEntity)
    @Query("SELECT max(finishedAt) FROM scan_history")
    fun lastScan():Flow<Long?>
}

@Database(
    entities=[
        SourceEntity::class,SourceHealthEntity::class,RawItemEntity::class,EventEntity::class,
        EventItemEntity::class,EventVersionEntity::class,EventEnrichmentEntity::class,
        ScanHistoryEntity::class
    ],
    version=15, exportSchema=false
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
        private val MIGRATION_9_10=object:Migration(9,10){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("DELETE FROM event_items")
                db.execSQL("DELETE FROM event_versions")
                db.execSQL("DELETE FROM events")
                db.execSQL("DELETE FROM raw_items")
            }
        }
        private val MIGRATION_10_11=object:Migration(10,11){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("DELETE FROM event_items")
                db.execSQL("DELETE FROM event_versions")
                db.execSQL("DELETE FROM events")
                db.execSQL("DELETE FROM raw_items")
            }
        }
        private val MIGRATION_11_12=object:Migration(11,12){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("DELETE FROM event_items")
                db.execSQL("DELETE FROM event_versions")
                db.execSQL("DELETE FROM events")
                db.execSQL("DELETE FROM raw_items")
            }
        }
        private val MIGRATION_12_13=object:Migration(12,13){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_items_sourceId ON raw_items(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_items_firstSeenAt ON raw_items(firstSeenAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_updatedAt ON events(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_scope_noise_updatedAt ON events(scope,noise,updatedAt)")
            }
        }
        private val MIGRATION_13_14=object:Migration(13,14){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_enrichment (
                        eventId TEXT NOT NULL,
                        sourceName TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        sourceTitle TEXT NOT NULL,
                        shortSummary TEXT NOT NULL,
                        whatHappened TEXT NOT NULL,
                        whyImportant TEXT NOT NULL,
                        latestSituation TEXT NOT NULL,
                        contentQuality REAL NOT NULL,
                        enrichedAt INTEGER NOT NULL,
                        PRIMARY KEY(eventId)
                    )
                """.trimIndent())
            }
        }
        private val MIGRATION_14_15=object:Migration(14,15){
            override fun migrate(db:SupportSQLiteDatabase){
                // v19: Önceki sürümlerde Din aramasına sızmış eski Google News
                // kayıtlarını bir defaya mahsus temizle. Türkiye/Dünya korunur.
                db.execSQL("""
                    DELETE FROM event_enrichment
                    WHERE eventId IN (
                        SELECT id FROM events
                        WHERE scope='religion' OR religionPriority>0
                    )
                """.trimIndent())
                db.execSQL("""
                    DELETE FROM event_versions
                    WHERE eventId IN (
                        SELECT id FROM events
                        WHERE scope='religion' OR religionPriority>0
                    )
                """.trimIndent())
                db.execSQL("""
                    DELETE FROM event_items
                    WHERE eventId IN (
                        SELECT id FROM events
                        WHERE scope='religion' OR religionPriority>0
                    )
                """.trimIndent())
                db.execSQL("""
                    DELETE FROM events
                    WHERE scope='religion' OR religionPriority>0
                """.trimIndent())
                db.execSQL("""
                    DELETE FROM raw_items
                    WHERE sourceId IN (
                        SELECT id FROM sources
                        WHERE groupName IN ('religion_search','religion_direct')
                    )
                """.trimIndent())
            }
        }
        fun get(context:Context)=INSTANCE?:synchronized(this){
            INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"gundem-radari.db")
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11,MIGRATION_11_12,MIGRATION_12_13,MIGRATION_13_14,MIGRATION_14_15)
                .build().also{INSTANCE=it}
        }
    }
}
