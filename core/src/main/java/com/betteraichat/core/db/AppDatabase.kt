package com.betteraichat.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val provider: String,
    val model: String,
    val mode: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val model: String? = null,
    val mode: String? = null,
    val status: String = "done",
    val usageInput: Long = 0,
    val usageOutput: Long = 0,
    val attachmentsJson: String? = null,
    val thinkingText: String? = null,
    val thinkingSignature: String? = null,
    val starred: Boolean = false,
    val createdAt: Long
)

@Dao
interface ConversationDao {
    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getAllCount(): Long

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinned(id: Long, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateArchived(id: Long, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getForConversation(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("SELECT DISTINCT conversationId FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY conversationId DESC")
    suspend fun searchConversationsByContent(query: String): List<Long>

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: Long, starred: Boolean)

    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY id DESC")
    fun observeStarred(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'USER'")
    suspend fun countUserMessages(): Long

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'ASSISTANT'")
    suspend fun countAssistantMessages(): Long

    @Query("SELECT COALESCE(SUM(usageInput), 0) AS totalInput, COALESCE(SUM(usageOutput), 0) AS totalOutput FROM messages")
    suspend fun tokenTotals(): TokenTotalsRow

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'ASSISTANT' AND toolCallsJson IS NOT NULL")
    suspend fun countToolCallMessages(): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id >= :fromId")
    suspend fun deleteFrom(conversationId: Long, fromId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id >= :fromId AND id <= :toId")
    suspend fun deleteRange(conversationId: Long, fromId: Long, toId: Long)

    @Query("DELETE FROM messages WHERE toolCallId IN (:toolCallIds)")
    suspend fun deleteByToolCallIds(toolCallIds: List<String>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: Long)
}

@Entity(tableName = "repeat_tasks")
data class RepeatTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val interval: String,
    val time: String = "",
    val weekday: Int = 0,
    val everyHours: Int = 1,
    val requestCode: Int,
    val nextTriggerAt: Long,
    val createdAt: Long
)

@Dao
interface RepeatTaskDao {
    @Query("SELECT * FROM repeat_tasks ORDER BY nextTriggerAt ASC")
    fun observeAll(): Flow<List<RepeatTaskEntity>>

    @Query("SELECT COUNT(*) FROM repeat_tasks")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM repeat_tasks")
    suspend fun getCount(): Long

    @Insert
    suspend fun insert(task: RepeatTaskEntity): Long

    @Query("DELETE FROM repeat_tasks WHERE requestCode = :requestCode")
    suspend fun deleteByRequestCode(requestCode: Int)

    @Query("UPDATE repeat_tasks SET nextTriggerAt = :nextTriggerAt WHERE requestCode = :requestCode")
    suspend fun updateNextTrigger(requestCode: Int, nextTriggerAt: Long)
}

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String,
    val triggerValue: String,
    val days: String,
    val actionsJson: String,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0,
    val createdAt: Long
)

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE enabled = 1")
    suspend fun getEnabled(): List<AutomationEntity>

    @Insert
    suspend fun insert(automation: AutomationEntity): Long

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE automations SET lastRunAt = :lastRunAt WHERE id = :id")
    suspend fun setLastRun(id: Long, lastRunAt: Long)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: Long)
}

data class TokenTotalsRow(
    val totalInput: Long = 0,
    val totalOutput: Long = 0
)

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, RepeatTaskEntity::class, AutomationEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun repeatTaskDao(): RepeatTaskDao
    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN usageInput INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN usageOutput INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN thinkingText TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN thinkingSignature TEXT")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS repeat_tasks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, content TEXT NOT NULL, interval TEXT NOT NULL, " +
                        "time TEXT NOT NULL, weekday INTEGER NOT NULL, everyHours INTEGER NOT NULL, " +
                        "requestCode INTEGER NOT NULL, nextTriggerAt INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS automations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, triggerType TEXT NOT NULL, triggerValue TEXT NOT NULL, " +
                        "days TEXT NOT NULL, actionsJson TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL, lastRunAt INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "betteraichat.db")
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
