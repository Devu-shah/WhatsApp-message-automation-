package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.data.local.db.entity.CampaignEntity
import com.example.data.local.db.entity.ContactCustomFieldEntity
import com.example.data.local.db.entity.ContactEntity
import com.example.data.local.db.entity.MediaAssetEntity
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.local.db.entity.MessageStatusEventEntity
import com.example.data.local.db.entity.SenderProfileEntity
import com.example.data.local.db.entity.SuppressionEntity
import com.example.data.local.db.entity.TemplateEntity
import com.example.domain.model.ConsentStatus
import com.example.domain.model.CsvCommitResult
import kotlinx.coroutines.flow.Flow

data class ContactWithCustomFieldsRelation(
  @Embedded val contact: ContactEntity,
  @Relation(parentColumn = "id", entityColumn = "contactId")
  val customFields: List<ContactCustomFieldEntity>,
)

@Dao
interface DashboardDao {
  @Query("SELECT COUNT(*) FROM contacts") fun observeTotalContacts(): Flow<Int>

  @Query("SELECT COUNT(*) FROM contacts WHERE isOptedOut = 1")
  fun observeOptedOutContacts(): Flow<Int>

  @Query("SELECT COUNT(*) FROM campaigns") fun observeTotalCampaigns(): Flow<Int>

  @Query("SELECT COUNT(*) FROM campaigns WHERE status IN ('RUNNING', 'PAUSED', 'READY')")
  fun observeActiveCampaigns(): Flow<Int>

  @Query("SELECT COUNT(*) FROM campaigns WHERE status = 'COMPLETED'")
  fun observeCompletedCampaigns(): Flow<Int>

  @Query("SELECT COUNT(*) FROM message_records WHERE submittedAt IS NOT NULL")
  fun observeTotalMessagesSubmitted(): Flow<Int>

  @Query("SELECT COUNT(*) FROM message_records WHERE currentStatus IN ('DELIVERED', 'READ')")
  fun observeMessagesDelivered(): Flow<Int>

  @Query("SELECT COUNT(*) FROM message_records WHERE currentStatus = 'READ'")
  fun observeMessagesRead(): Flow<Int>

  @Query("SELECT COUNT(*) FROM message_records WHERE currentStatus = 'FAILED'")
  fun observeFailedMessages(): Flow<Int>

  @Query("SELECT COUNT(*) FROM templates") fun observeTotalTemplates(): Flow<Int>

  @Query("SELECT COUNT(*) FROM media_assets") fun observeTotalMediaAssets(): Flow<Int>

  @Query("SELECT * FROM campaigns ORDER BY createdAt DESC LIMIT :limit")
  fun observeRecentCampaigns(limit: Int = 5): Flow<List<CampaignEntity>>

  @Query("SELECT * FROM message_records ORDER BY updatedAt DESC LIMIT :limit")
  fun observeRecentMessageRecords(limit: Int = 8): Flow<List<MessageRecordEntity>>
}

@Dao
interface ContactDao {
  @Query("SELECT * FROM contacts ORDER BY importedAt DESC")
  fun observeAllContacts(): Flow<List<ContactEntity>>

  @Transaction
  @Query("SELECT * FROM contacts ORDER BY importedAt DESC")
  fun observeContactsWithFields(): Flow<List<ContactWithCustomFieldsRelation>>

  @Query("SELECT * FROM suppression_list ORDER BY optedOutAt DESC")
  fun observeSuppressionList(): Flow<List<SuppressionEntity>>

  @Query("SELECT phoneE164 FROM contacts")
  suspend fun getAllPhoneNumbers(): List<String>

  @Query("SELECT phoneE164 FROM suppression_list")
  suspend fun getAllSuppressedPhones(): List<String>

  @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
  suspend fun findById(id: Long): ContactEntity?

  @Query("SELECT * FROM contacts WHERE phoneE164 = :phoneE164 LIMIT 1")
  suspend fun findByPhone(phoneE164: String): ContactEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertContact(contact: ContactEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCustomFields(fields: List<ContactCustomFieldEntity>)

  @Query("DELETE FROM contact_custom_fields WHERE contactId = :contactId")
  suspend fun deleteCustomFieldsForContact(contactId: Long)

  @Query("DELETE FROM contacts WHERE id = :contactId")
  suspend fun deleteContactById(contactId: Long)

  @Query("SELECT EXISTS(SELECT 1 FROM suppression_list WHERE phoneE164 = :phoneE164)")
  suspend fun isSuppressed(phoneE164: String): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addSuppression(suppression: SuppressionEntity)

  @Query("DELETE FROM suppression_list WHERE phoneE164 = :phoneE164")
  suspend fun removeSuppression(phoneE164: String)

  @Query(
    "UPDATE contacts SET isOptedOut = :optedOut, consentStatus = :consentStatus WHERE id = :contactId"
  )
  suspend fun updateContactOptOut(
    contactId: Long,
    optedOut: Boolean,
    consentStatus: ConsentStatus,
  )

  @Query(
    "UPDATE contacts SET isOptedOut = 0, consentStatus = 'GRANTED' WHERE phoneE164 = :phoneE164"
  )
  suspend fun clearOptOutByPhone(phoneE164: String)

  @Transaction
  suspend fun upsertContactWithFields(
    contact: ContactEntity,
    customFields: Map<String, String>,
  ): Long {
    val existingById = if (contact.id != 0L) findById(contact.id) else null
    val existingByPhone = findByPhone(contact.phoneE164)
    val existing = existingById ?: existingByPhone
    val targetId =
      if (contact.id != 0L) {
        val preserved =
          contact.copy(
            importStatus = existingById?.importStatus ?: contact.importStatus,
            importedAt = existingById?.importedAt ?: contact.importedAt,
          )
        upsertContact(preserved)
        contact.id
      } else if (existing != null) {
        upsertContact(
          contact.copy(
            id = existing.id,
            importStatus = existing.importStatus,
            importedAt = existing.importedAt,
          )
        )
        existing.id
      } else {
        upsertContact(contact)
      }
    deleteCustomFieldsForContact(targetId)
    if (customFields.isNotEmpty()) {
      insertCustomFields(
        customFields.entries.map { (k, v) ->
          ContactCustomFieldEntity(contactId = targetId, fieldKey = k, fieldValue = v)
        }
      )
    }
    if (contact.isOptedOut) {
      addSuppression(
        SuppressionEntity(
          phoneE164 = contact.phoneE164,
          reason = "Marked opted-out by operator",
          source = "operator",
        )
      )
    } else {
      removeSuppression(contact.phoneE164)
    }
    return targetId
  }

  @Query("SELECT DISTINCT fieldKey FROM contact_custom_fields ORDER BY fieldKey ASC")
  fun observeDistinctCustomFieldKeys(): Flow<List<String>>

  @Transaction
  suspend fun commitCsvBatch(
    rows: List<Pair<ContactEntity, Map<String, String>>>,
    updateExisting: Boolean,
  ): CsvCommitResult {
    var inserted = 0
    var updated = 0
    var skipped = 0
    for ((contact, fields) in rows) {
      if (isSuppressed(contact.phoneE164)) {
        skipped++
        continue
      }
      val existing = findByPhone(contact.phoneE164)
      if (existing != null) {
        if (!updateExisting) {
          skipped++
          continue
        }
        val merged =
          contact.copy(
            id = existing.id,
            isOptedOut = existing.isOptedOut || contact.isOptedOut,
            importStatus = "CSV_UPDATED",
          )
        upsertContact(merged)
        deleteCustomFieldsForContact(existing.id)
        if (fields.isNotEmpty()) {
          insertCustomFields(
            fields.entries.map { (k, v) ->
              ContactCustomFieldEntity(contactId = existing.id, fieldKey = k, fieldValue = v)
            }
          )
        }
        updated++
      } else {
        val newId = upsertContact(contact.copy(importStatus = "CSV_IMPORTED"))
        if (fields.isNotEmpty()) {
          insertCustomFields(
            fields.entries.map { (k, v) ->
              ContactCustomFieldEntity(contactId = newId, fieldKey = k, fieldValue = v)
            }
          )
        }
        if (contact.isOptedOut) {
          addSuppression(
            SuppressionEntity(
              phoneE164 = contact.phoneE164,
              reason = "CSV consent marked revoked",
              source = "csv_import",
            )
          )
        }
        inserted++
      }
    }
    return CsvCommitResult(insertedCount = inserted, updatedCount = updated, skippedCount = skipped)
  }

  @Transaction
  suspend fun setContactOptOut(
    contactId: Long,
    phoneE164: String,
    optedOut: Boolean,
    reason: String,
    source: String,
  ) {
    if (optedOut) {
      updateContactOptOut(contactId, optedOut = true, consentStatus = ConsentStatus.REVOKED)
      addSuppression(SuppressionEntity(phoneE164 = phoneE164, reason = reason, source = source))
    } else {
      updateContactOptOut(contactId, optedOut = false, consentStatus = ConsentStatus.GRANTED)
      removeSuppression(phoneE164)
    }
  }

  @Transaction
  suspend fun removeSuppressionAndRestoreContact(phoneE164: String) {
    removeSuppression(phoneE164)
    clearOptOutByPhone(phoneE164)
  }
}

@Dao
interface TemplateDao {
  @Query("SELECT * FROM templates ORDER BY updatedAt DESC")
  fun observeAllTemplates(): Flow<List<TemplateEntity>>

  @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
  suspend fun getTemplateById(id: Long): TemplateEntity?

  @Query("SELECT * FROM templates WHERE name = :name LIMIT 1")
  suspend fun findByName(name: String): TemplateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTemplate(template: TemplateEntity): Long

  @Query("DELETE FROM templates WHERE id = :templateId")
  suspend fun deleteTemplateById(templateId: Long)

  @Query("SELECT COUNT(*) FROM campaigns WHERE templateId = :templateId")
  suspend fun countCampaignsUsingTemplate(templateId: Long): Int
}

@Dao
interface MediaDao {
  @Query("SELECT * FROM media_assets ORDER BY createdAt DESC")
  fun observeAllMediaAssets(): Flow<List<MediaAssetEntity>>

  @Query("SELECT * FROM media_assets WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): MediaAssetEntity?

  @Query("SELECT * FROM media_assets WHERE sha256Hash = :sha256Hash LIMIT 1")
  suspend fun findBySha256(sha256Hash: String): MediaAssetEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMediaAsset(asset: MediaAssetEntity): Long

  @Query(
    "UPDATE media_assets SET displayName = :displayName, captionNote = :captionNote WHERE id = :id"
  )
  suspend fun updateMetadata(id: Long, displayName: String, captionNote: String?)

  @Query(
    "UPDATE media_assets SET metaMediaId = :metaMediaId, uploadStatus = 'UPLOADED', uploadedAt = :uploadedAt WHERE id = :id"
  )
  suspend fun markMediaUploaded(id: Long, metaMediaId: String, uploadedAt: Long)

  @Query("DELETE FROM media_assets WHERE id = :mediaId")
  suspend fun deleteMediaAssetById(mediaId: Long)

  @Query("SELECT COUNT(*) FROM campaigns WHERE mediaAssetId = :mediaId")
  suspend fun countCampaignsUsingMedia(mediaId: Long): Int
}

@Dao
interface CampaignDao {
  @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
  fun observeAllCampaigns(): Flow<List<CampaignEntity>>

  @Query("SELECT * FROM campaigns WHERE id = :campaignId LIMIT 1")
  suspend fun getCampaignById(campaignId: Long): CampaignEntity?

  @Query("SELECT * FROM campaigns WHERE id = :campaignId LIMIT 1")
  fun observeCampaignById(campaignId: Long): Flow<CampaignEntity?>

  @Query("SELECT COUNT(*) FROM campaigns WHERE mode = 'SIMULATION' AND status = 'COMPLETED'")
  fun observeCompletedSimulationCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM campaigns WHERE mode = 'SIMULATION' AND status = 'COMPLETED'")
  suspend fun countCompletedSimulations(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCampaign(campaign: CampaignEntity): Long

  @Query("UPDATE campaigns SET status = :status WHERE id = :campaignId")
  suspend fun updateCampaignStatus(campaignId: Long, status: com.example.domain.model.CampaignStatus)

  @Query(
    "UPDATE campaigns SET status = 'RUNNING', startedAt = COALESCE(startedAt, :startedAt) WHERE id = :campaignId"
  )
  suspend fun markCampaignRunning(campaignId: Long, startedAt: Long)

  @Query(
    "UPDATE campaigns SET status = :finalStatus, completedAt = :completedAt WHERE id = :campaignId"
  )
  suspend fun markCampaignFinished(
    campaignId: Long,
    finalStatus: com.example.domain.model.CampaignStatus,
    completedAt: Long,
  )

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCampaignRecipients(
    recipients: List<com.example.data.local.db.entity.CampaignRecipientEntity>
  )

  @Query("SELECT * FROM campaign_recipients WHERE campaignId = :campaignId")
  suspend fun getCampaignRecipients(
    campaignId: Long
  ): List<com.example.data.local.db.entity.CampaignRecipientEntity>

  @Query("SELECT * FROM campaign_recipients WHERE campaignId = :campaignId")
  fun observeCampaignRecipients(
    campaignId: Long
  ): Flow<List<com.example.data.local.db.entity.CampaignRecipientEntity>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertMessageRecords(records: List<MessageRecordEntity>): List<Long>

  @Query("SELECT * FROM message_records WHERE campaignId = :campaignId ORDER BY id ASC")
  fun observeMessageRecordsForCampaign(campaignId: Long): Flow<List<MessageRecordEntity>>

  @Query("SELECT * FROM message_records WHERE campaignId = :campaignId ORDER BY id ASC")
  suspend fun getMessageRecordsForCampaign(campaignId: Long): List<MessageRecordEntity>

  @Query("DELETE FROM campaigns WHERE id = :campaignId")
  suspend fun deleteCampaignById(campaignId: Long)

  @Transaction
  suspend fun cloneCampaignAsDraft(
    sourceCampaignId: Long,
    newName: String,
    targetMode: com.example.domain.model.AppMode,
  ): Long? {
    val source = getCampaignById(sourceCampaignId) ?: return null
    val now = System.currentTimeMillis()
    val clonedId =
      upsertCampaign(
        CampaignEntity(
          id = 0,
          name = newName,
          description = source.description,
          templateId = source.templateId,
          mediaAssetId = source.mediaAssetId,
          senderProfileId = source.senderProfileId,
          senderPhoneSnapshot = source.senderPhoneSnapshot,
          status = com.example.domain.model.CampaignStatus.DRAFT,
          mode = targetMode,
          scheduledAt = null,
          startedAt = null,
          completedAt = null,
          createdAt = now,
        )
      )
    val recipients = getCampaignRecipients(sourceCampaignId)
    if (recipients.isNotEmpty()) {
      insertCampaignRecipients(recipients.map { it.copy(campaignId = clonedId) })
    }
    return clonedId
  }
}

@Dao
interface MessageLogDao {
  @Query("SELECT * FROM message_records ORDER BY updatedAt DESC")
  fun observeAllMessageRecords(): Flow<List<MessageRecordEntity>>

  @Query("SELECT * FROM message_status_events WHERE messageId = :messageId ORDER BY eventTimestamp ASC")
  fun observeEventsForMessage(messageId: Long): Flow<List<MessageStatusEventEntity>>

  @Query("SELECT * FROM retry_attempts WHERE messageId = :messageId ORDER BY attemptNumber ASC")
  fun observeRetryAttemptsForMessage(
    messageId: Long
  ): Flow<List<com.example.data.local.db.entity.RetryAttemptEntity>>

  @Query("SELECT * FROM message_records WHERE id = :messageId LIMIT 1")
  suspend fun getMessageRecordById(messageId: Long): MessageRecordEntity?

  @Query("SELECT * FROM message_records WHERE idempotencyKey = :idempotencyKey LIMIT 1")
  suspend fun getMessageRecordByIdempotencyKey(idempotencyKey: String): MessageRecordEntity?

  @Query("SELECT * FROM message_records WHERE whatsappMessageId = :wamid LIMIT 1")
  suspend fun getMessageRecordByWamid(wamid: String): MessageRecordEntity?

  @Query(
    "SELECT * FROM message_records WHERE campaignId = :campaignId AND currentStatus = 'QUEUED' ORDER BY id ASC LIMIT :limit"
  )
  suspend fun getNextQueuedBatch(campaignId: Long, limit: Int): List<MessageRecordEntity>

  @Query(
    "SELECT * FROM message_records WHERE campaignId = :campaignId AND currentStatus = 'FAILED' AND isPermanentError = 0 AND attemptCount < :maxAttempts ORDER BY updatedAt ASC"
  )
  suspend fun getRetryableFailedMessagesForCampaign(
    campaignId: Long,
    maxAttempts: Int,
  ): List<MessageRecordEntity>

  @Query(
    "SELECT * FROM message_records WHERE currentStatus = 'FAILED' AND isPermanentError = 0 AND attemptCount < :maxAttempts ORDER BY updatedAt ASC"
  )
  suspend fun getRetryableFailedMessagesGlobal(maxAttempts: Int): List<MessageRecordEntity>

  @Query("SELECT COUNT(*) FROM message_records WHERE currentStatus IN ('FAILED') AND isPermanentError = 1")
  suspend fun countVerifiedPermanentFailures(): Int

  @androidx.room.Update suspend fun updateMessageRecord(record: MessageRecordEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStatusEvent(event: MessageStatusEventEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRetryAttempt(attempt: com.example.data.local.db.entity.RetryAttemptEntity): Long

  @androidx.room.Update
  suspend fun updateRetryAttempt(attempt: com.example.data.local.db.entity.RetryAttemptEntity)

  @Transaction
  suspend fun applyMonotonicStatusTransition(
    messageId: Long,
    targetStatus: com.example.domain.model.MessageStatus,
    eventTimestamp: Long,
    source: String,
    detail: String? = null,
    whatsappMessageId: String? = null,
    errorCode: String? = null,
    errorDescription: String? = null,
    isPermanentError: Boolean? = null,
    incrementAttempt: Boolean = false,
    apiResponseSummary: String? = null,
  ) {
    val current = getMessageRecordById(messageId) ?: return
    // Always append audit trail event cleanly
    insertStatusEvent(
      MessageStatusEventEntity(
        messageId = messageId,
        status = targetStatus,
        eventTimestamp = eventTimestamp,
        source = source,
        detail = detail ?: errorDescription,
      )
    )

    val resolvedStatus =
      if (shouldAdvanceStatus(current.currentStatus, targetStatus)) targetStatus
      else current.currentStatus

    val updated =
      current.copy(
        currentStatus = resolvedStatus,
        whatsappMessageId = whatsappMessageId ?: current.whatsappMessageId,
        errorCode =
          if (resolvedStatus == com.example.domain.model.MessageStatus.FAILED)
            errorCode ?: current.errorCode
          else null,
        errorDescription =
          if (resolvedStatus == com.example.domain.model.MessageStatus.FAILED)
            errorDescription ?: current.errorDescription
          else null,
        isPermanentError =
          if (resolvedStatus == com.example.domain.model.MessageStatus.FAILED)
            (isPermanentError ?: current.isPermanentError)
          else false,
        attemptCount = if (incrementAttempt) current.attemptCount + 1 else current.attemptCount,
        apiResponseSummary = apiResponseSummary ?: current.apiResponseSummary,
        submittedAt =
          if (
            targetStatus == com.example.domain.model.MessageStatus.SUBMITTED &&
              current.submittedAt == null
          )
            eventTimestamp
          else current.submittedAt,
        sentAt =
          if (
            targetStatus == com.example.domain.model.MessageStatus.SENT && current.sentAt == null
          )
            eventTimestamp
          else current.sentAt,
        deliveredAt =
          if (
            targetStatus == com.example.domain.model.MessageStatus.DELIVERED &&
              current.deliveredAt == null
          )
            eventTimestamp
          else current.deliveredAt,
        readAt =
          if (
            targetStatus == com.example.domain.model.MessageStatus.READ && current.readAt == null
          )
            eventTimestamp
          else current.readAt,
        updatedAt = maxOf(current.updatedAt, eventTimestamp),
      )
    updateMessageRecord(updated)
  }

  companion object {
    private fun progressionRank(status: com.example.domain.model.MessageStatus): Int =
      when (status) {
        com.example.domain.model.MessageStatus.QUEUED -> 0
        com.example.domain.model.MessageStatus.SUBMITTED -> 1
        com.example.domain.model.MessageStatus.SENT -> 2
        com.example.domain.model.MessageStatus.DELIVERED -> 3
        com.example.domain.model.MessageStatus.READ -> 4
        com.example.domain.model.MessageStatus.FAILED -> 1
        com.example.domain.model.MessageStatus.SKIPPED -> 1
      }

    fun shouldAdvanceStatus(
      current: com.example.domain.model.MessageStatus,
      incoming: com.example.domain.model.MessageStatus,
    ): Boolean {
      if (current == incoming) return true
      // Allow explicit retry re-queue of a non-delivered failed message
      if (
        current == com.example.domain.model.MessageStatus.FAILED &&
          incoming == com.example.domain.model.MessageStatus.QUEUED
      ) {
        return true
      }
      if (
        incoming == com.example.domain.model.MessageStatus.FAILED ||
          incoming == com.example.domain.model.MessageStatus.SKIPPED
      ) {
        return current != com.example.domain.model.MessageStatus.DELIVERED &&
          current != com.example.domain.model.MessageStatus.READ
      }
      if (
        current == com.example.domain.model.MessageStatus.FAILED ||
          current == com.example.domain.model.MessageStatus.SKIPPED
      ) {
        // Delayed delivery webhook or manual recovery can overtake FAILED/SKIPPED
        return progressionRank(incoming) >= 2
      }
      return progressionRank(incoming) >= progressionRank(current)
    }
  }
}

@Dao
interface SenderProfileDao {
  @Query("SELECT * FROM sender_profiles ORDER BY isDefault DESC, createdAt DESC")
  fun observeAllSenderProfiles(): Flow<List<SenderProfileEntity>>

  @Query("SELECT * FROM sender_profiles WHERE isDefault = 1 LIMIT 1")
  fun observeDefaultSenderProfile(): Flow<SenderProfileEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSenderProfile(profile: SenderProfileEntity): Long

  @Query("UPDATE sender_profiles SET isDefault = 0")
  suspend fun clearDefaultFlags()

  @Query("UPDATE sender_profiles SET isDefault = 1 WHERE id = :profileId")
  suspend fun markAsDefault(profileId: Long)

  @Transaction
  suspend fun setDefaultProfile(profileId: Long) {
    clearDefaultFlags()
    markAsDefault(profileId)
  }

  @Query("DELETE FROM sender_profiles WHERE id = :profileId")
  suspend fun deleteSenderProfile(profileId: Long)
}
