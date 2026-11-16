package com.example.data.local.db

import androidx.room.TypeConverter
import com.example.domain.model.AppMode
import com.example.domain.model.CampaignStatus
import com.example.domain.model.ConsentStatus
import com.example.domain.model.MediaUploadStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.RecipientEligibility
import com.example.domain.model.RetryOutcome
import com.example.domain.model.TemplateValidationStatus

class ReachHubConverters {
  @TypeConverter fun fromAppMode(value: AppMode): String = value.name

  @TypeConverter fun toAppMode(value: String): AppMode = AppMode.valueOf(value)

  @TypeConverter fun fromConsentStatus(value: ConsentStatus): String = value.name

  @TypeConverter
  fun toConsentStatus(value: String): ConsentStatus = ConsentStatus.valueOf(value)

  @TypeConverter fun fromCampaignStatus(value: CampaignStatus): String = value.name

  @TypeConverter
  fun toCampaignStatus(value: String): CampaignStatus = CampaignStatus.valueOf(value)

  @TypeConverter fun fromRecipientEligibility(value: RecipientEligibility): String = value.name

  @TypeConverter
  fun toRecipientEligibility(value: String): RecipientEligibility =
    RecipientEligibility.valueOf(value)

  @TypeConverter fun fromMessageStatus(value: MessageStatus): String = value.name

  @TypeConverter
  fun toMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)

  @TypeConverter
  fun fromTemplateValidationStatus(value: TemplateValidationStatus): String = value.name

  @TypeConverter
  fun toTemplateValidationStatus(value: String): TemplateValidationStatus =
    TemplateValidationStatus.valueOf(value)

  @TypeConverter fun fromMediaUploadStatus(value: MediaUploadStatus): String = value.name

  @TypeConverter
  fun toMediaUploadStatus(value: String): MediaUploadStatus = MediaUploadStatus.valueOf(value)

  @TypeConverter fun fromRetryOutcome(value: RetryOutcome): String = value.name

  @TypeConverter fun toRetryOutcome(value: String): RetryOutcome = RetryOutcome.valueOf(value)
}
