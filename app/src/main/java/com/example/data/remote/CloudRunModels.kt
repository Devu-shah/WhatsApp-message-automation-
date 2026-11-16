package com.example.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequest(
  val keyFingerprint: String,
  val publicKeyBase64: String,
  val deviceLabel: String,
  val appVersion: String,
)

@Serializable
data class DeviceRegistrationResponse(
  val registered: Boolean,
  val keyFingerprint: String,
  val message: String = "",
)

@Serializable
data class CloudRunReadinessResponse(
  val ok: Boolean,
  val deviceRegistered: Boolean = false,
  val metaSecretsConfigured: Boolean = false,
  val webhookVerifyConfigured: Boolean = false,
  val cloudTasksConfigured: Boolean = false,
  val senderProfilesConfigured: List<String> = emptyList(),
  val serverTimestampMs: Long = 0L,
  val message: String = "",
)

@Serializable
data class MediaUploadResponse(
  val metaMediaId: String,
  val sha256Hash: String,
  val deduplicated: Boolean = false,
)

@Serializable
data class DispatchRecipientPayload(
  val localMessageId: Long,
  val recipientName: String,
  val recipientPhoneE164: String,
  val idempotencyKey: String,
  val positionalParameters: List<String>,
)

@Serializable
data class DispatchBatchRequest(
  val campaignId: Long,
  val campaignName: String,
  val senderProfileRef: String,
  val senderPhoneE164: String,
  val metaTemplateName: String,
  val languageCode: String,
  val metaMediaId: String? = null,
  val recipients: List<DispatchRecipientPayload>,
)

@Serializable
data class DispatchRecipientResult(
  val localMessageId: Long,
  val idempotencyKey: String,
  val status: String, // SUBMITTED | FAILED | QUEUED
  val whatsappMessageId: String? = null,
  val errorCode: String? = null,
  val errorDescription: String? = null,
  val isPermanentError: Boolean = false,
  val apiResponseSummary: String? = null,
)

@Serializable
data class DispatchBatchResponse(
  val campaignId: Long,
  val acceptedCount: Int = 0,
  val results: List<DispatchRecipientResult> = emptyList(),
)

@Serializable
data class CampaignControlRequest(
  val action: String // PAUSE | RESUME | CANCEL
)

@Serializable
data class CampaignControlResponse(
  val campaignId: Long,
  val action: String,
  val acknowledged: Boolean,
)

@Serializable
data class ServerStatusEvent(
  val whatsappMessageId: String,
  val idempotencyKey: String? = null,
  val status: String, // SENT | DELIVERED | READ | FAILED
  val eventTimestampMs: Long,
  val errorCode: String? = null,
  val errorDescription: String? = null,
  val isPermanentError: Boolean = false,
  val source: String = "meta_webhook",
)

@Serializable
data class CampaignStatusSyncResponse(
  val campaignId: Long,
  val events: List<ServerStatusEvent> = emptyList(),
  val nextCursorMs: Long = 0L,
)
