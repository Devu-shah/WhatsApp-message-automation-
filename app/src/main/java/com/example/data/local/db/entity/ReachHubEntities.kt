package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AppMode
import com.example.domain.model.CampaignStatus
import com.example.domain.model.ConsentStatus
import com.example.domain.model.MediaUploadStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.RecipientEligibility
import com.example.domain.model.RetryOutcome
import com.example.domain.model.TemplateValidationStatus

@Entity(
  tableName = "contacts",
  indices = [Index(value = ["phoneE164"], unique = true), Index(value = ["isOptedOut"])],
)
data class ContactEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val phoneE164: String,
  val rawPhone: String,
  val email: String? = null,
  val college: String? = null,
  val city: String? = null,
  val state: String? = null,
  val consentStatus: ConsentStatus = ConsentStatus.GRANTED,
  val isOptedOut: Boolean = false,
  val importStatus: String = "MANUAL",
  val importedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "contact_custom_fields",
  foreignKeys =
    [
      ForeignKey(
        entity = ContactEntity::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["contactId"]), Index(value = ["fieldKey"])],
)
data class ContactCustomFieldEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val contactId: Long,
  val fieldKey: String,
  val fieldValue: String,
)

@Entity(tableName = "suppression_list")
data class SuppressionEntity(
  @PrimaryKey val phoneE164: String,
  val reason: String,
  val source: String,
  val optedOutAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "templates", indices = [Index(value = ["name"], unique = true)])
data class TemplateEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val metaTemplateName: String,
  val languageCode: String = "en",
  val category: String = "MARKETING",
  val bodyText: String,
  val placeholdersJson: String = "[]",
  val parameterMappingJson: String = "{}",
  val headerMediaRequired: Boolean = false,
  val validationStatus: TemplateValidationStatus = TemplateValidationStatus.NEEDS_MAPPING,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "media_assets", indices = [Index(value = ["sha256Hash"], unique = true)])
data class MediaAssetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val displayName: String,
  val localUri: String,
  val mimeType: String,
  val byteSize: Long,
  val sha256Hash: String,
  val metaMediaId: String? = null,
  val captionNote: String? = null,
  val widthPx: Int = 0,
  val heightPx: Int = 0,
  val uploadStatus: MediaUploadStatus = MediaUploadStatus.LOCAL_ONLY,
  val uploadedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "sender_profiles",
  indices = [Index(value = ["phoneNumberE164"], unique = true)],
)
data class SenderProfileEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val label: String,
  val phoneNumberE164: String,
  val cloudRunProfileRef: String,
  val isDefault: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "campaigns",
  foreignKeys =
    [
      ForeignKey(
        entity = TemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.RESTRICT,
      ),
      ForeignKey(
        entity = MediaAssetEntity::class,
        parentColumns = ["id"],
        childColumns = ["mediaAssetId"],
        onDelete = ForeignKey.SET_NULL,
      ),
      ForeignKey(
        entity = SenderProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["senderProfileId"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices =
    [
      Index(value = ["templateId"]),
      Index(value = ["mediaAssetId"]),
      Index(value = ["senderProfileId"]),
      Index(value = ["status"]),
    ],
)
data class CampaignEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val description: String = "",
  val templateId: Long,
  val mediaAssetId: Long? = null,
  val senderProfileId: Long? = null,
  val senderPhoneSnapshot: String = "",
  val status: CampaignStatus = CampaignStatus.DRAFT,
  val mode: AppMode = AppMode.SIMULATION,
  val scheduledAt: Long? = null,
  val startedAt: Long? = null,
  val completedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "campaign_recipients",
  primaryKeys = ["campaignId", "contactId"],
  foreignKeys =
    [
      ForeignKey(
        entity = CampaignEntity::class,
        parentColumns = ["id"],
        childColumns = ["campaignId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ContactEntity::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices = [Index(value = ["contactId"]), Index(value = ["eligibilityStatus"])],
)
data class CampaignRecipientEntity(
  val campaignId: Long,
  val contactId: Long,
  val eligibilityStatus: RecipientEligibility,
  val selected: Boolean = true,
)

@Entity(
  tableName = "message_records",
  foreignKeys =
    [
      ForeignKey(
        entity = CampaignEntity::class,
        parentColumns = ["id"],
        childColumns = ["campaignId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ContactEntity::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices =
    [
      Index(value = ["campaignId"]),
      Index(value = ["contactId"]),
      Index(value = ["currentStatus"]),
      Index(value = ["idempotencyKey"], unique = true),
      Index(value = ["whatsappMessageId"]),
    ],
)
data class MessageRecordEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val campaignId: Long,
  val contactId: Long?,
  val recipientName: String,
  val recipientPhoneE164: String,
  val personalizedBody: String,
  val mediaReference: String? = null,
  val whatsappMessageId: String? = null,
  val idempotencyKey: String,
  val currentStatus: MessageStatus = MessageStatus.QUEUED,
  val apiResponseSummary: String? = null,
  val errorCode: String? = null,
  val errorDescription: String? = null,
  val isPermanentError: Boolean = false,
  val attemptCount: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val submittedAt: Long? = null,
  val sentAt: Long? = null,
  val deliveredAt: Long? = null,
  val readAt: Long? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "message_status_events",
  foreignKeys =
    [
      ForeignKey(
        entity = MessageRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["messageId"]), Index(value = ["eventTimestamp"])],
)
data class MessageStatusEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val messageId: Long,
  val status: MessageStatus,
  val eventTimestamp: Long = System.currentTimeMillis(),
  val source: String,
  val detail: String? = null,
)

@Entity(
  tableName = "retry_attempts",
  foreignKeys =
    [
      ForeignKey(
        entity = MessageRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["messageId"])],
)
data class RetryAttemptEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val messageId: Long,
  val attemptNumber: Int,
  val scheduledAt: Long,
  val executedAt: Long? = null,
  val outcome: RetryOutcome = RetryOutcome.PENDING,
  val errorCode: String? = null,
  val errorDetail: String? = null,
)
