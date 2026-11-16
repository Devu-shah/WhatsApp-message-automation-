package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.db.dao.CampaignDao
import com.example.data.local.db.dao.ContactDao
import com.example.data.local.db.dao.DashboardDao
import com.example.data.local.db.dao.MediaDao
import com.example.data.local.db.dao.MessageLogDao
import com.example.data.local.db.dao.SenderProfileDao
import com.example.data.local.db.dao.TemplateDao
import com.example.data.local.db.entity.CampaignEntity
import com.example.data.local.db.entity.CampaignRecipientEntity
import com.example.data.local.db.entity.ContactCustomFieldEntity
import com.example.data.local.db.entity.ContactEntity
import com.example.data.local.db.entity.MediaAssetEntity
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.local.db.entity.MessageStatusEventEntity
import com.example.data.local.db.entity.RetryAttemptEntity
import com.example.data.local.db.entity.SenderProfileEntity
import com.example.data.local.db.entity.SuppressionEntity
import com.example.data.local.db.entity.TemplateEntity

@Database(
  entities =
    [
      ContactEntity::class,
      ContactCustomFieldEntity::class,
      SuppressionEntity::class,
      TemplateEntity::class,
      MediaAssetEntity::class,
      SenderProfileEntity::class,
      CampaignEntity::class,
      CampaignRecipientEntity::class,
      MessageRecordEntity::class,
      MessageStatusEventEntity::class,
      RetryAttemptEntity::class,
    ],
  version = 2,
  exportSchema = false,
)
@TypeConverters(ReachHubConverters::class)
abstract class ReachHubDatabase : RoomDatabase() {
  abstract fun dashboardDao(): DashboardDao

  abstract fun contactDao(): ContactDao

  abstract fun templateDao(): TemplateDao

  abstract fun mediaDao(): MediaDao

  abstract fun campaignDao(): CampaignDao

  abstract fun messageLogDao(): MessageLogDao

  abstract fun senderProfileDao(): SenderProfileDao

  companion object {
    const val DATABASE_NAME = "reachhub_local.db"

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE contacts ADD COLUMN importStatus TEXT NOT NULL DEFAULT 'MANUAL'"
          )
          db.execSQL("ALTER TABLE media_assets ADD COLUMN captionNote TEXT")
          db.execSQL("ALTER TABLE media_assets ADD COLUMN widthPx INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE media_assets ADD COLUMN heightPx INTEGER NOT NULL DEFAULT 0")
        }
      }

    @Volatile private var instance: ReachHubDatabase? = null

    fun getInstance(context: Context): ReachHubDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                ReachHubDatabase::class.java,
                DATABASE_NAME,
              )
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { instance = it }
        }
  }
}
