package com.potato.player.engine

import androidx.room.*

@Entity(tableName = "playback_positions")
data class PlaybackPosition(
    @PrimaryKey val filePath: String,
    val position: Long
)

@Dao
interface PlaybackDao {
    @Query("SELECT position FROM playback_positions WHERE filePath = :filePath")
    suspend fun getPosition(filePath: String): Long?

    @Upsert
    suspend fun savePosition(playbackPosition: PlaybackPosition)
}

private const val DB_VERSION = 1

@Database(entities = [PlaybackPosition::class], version = DB_VERSION, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        init {
            check(DB_VERSION == 1) {
                "DB_VERSION is $DB_VERSION â€” add a Room migration and remove fallbackToDestructiveMigration() before bumping the version."
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vora_player_db"
                )
                // WARNING: fallbackToDestructiveMigration will silently wipe ALL saved
                // playback positions if the DB version is ever bumped without a migration.
                // Before incrementing 'version' in @Database, add a proper migration:
                //   .addMigrations(MIGRATION_1_2, ...)
                // and remove this call.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class VideoRepository(private val dao: PlaybackDao) {
    suspend fun getPosition(uri: String) = dao.getPosition(uri) ?: 0L
    suspend fun savePosition(uri: String, position: Long) =
        dao.savePosition(PlaybackPosition(uri, position))
}