package com.example.domain.model

enum class AppMode {
  SIMULATION,
  PRODUCTION,
}

enum class ConsentStatus {
  GRANTED,
  PENDING,
  REVOKED,
  UNKNOWN,
}

enum class CampaignStatus {
  DRAFT,
  READY,
  RUNNING,
  PAUSED,
  COMPLETED,
  CANCELLED,
  FAILED,
}

enum class RecipientEligibility {
  VALID,
  INVALID,
  DUPLICATE,
  OPTED_OUT,
}

enum class MessageStatus {
  QUEUED,
  SUBMITTED,
  SENT,
  DELIVERED,
  READ,
  FAILED,
  SKIPPED,
}

enum class TemplateValidationStatus {
  VALID,
  NEEDS_MAPPING,
  INVALID,
}

enum class MediaUploadStatus {
  LOCAL_ONLY,
  UPLOADING,
  UPLOADED,
  ERROR,
}

enum class RetryOutcome {
  PENDING,
  SUCCEEDED,
  TEMP_ERROR,
  PERM_ERROR,
}
