package com.example.domain.model

data class OperatorSettings(
  val defaultCountryCode: String = "+91",
  val autoApplyDefaultCountryCode: Boolean = true,
  val simulationModeEnabled: Boolean = true,
  val cloudRunBaseUrl: String = "",
  val batchSize: Int = 50,
  val maxRetryAttempts: Int = 3,
  val operatorTestNumber: String = "",
  val notificationsEnabled: Boolean = true,
  val dataRetentionDays: Int = 180,
  val cloudRunDeviceRegistered: Boolean = false,
  val cloudRunMetaSecretsVerified: Boolean = false,
  val cloudRunWebhookVerified: Boolean = false,
  val cloudRunQueueConfigured: Boolean = false,
  val gcpBudgetAlertConfirmed: Boolean = false,
  val lastReadinessCheckedAt: Long? = null,
)

/**
 * Non-secret WhatsApp Business API sender profile configured by the operator.
 * Private Meta Access Tokens and App Secrets are NEVER stored on the Android client;
 * they live exclusively in Google Cloud Secret Manager and are resolved by Cloud Run
 * using [cloudRunProfileRef].
 */
data class SenderProfile(
  val id: Long = 0,
  val label: String,
  val phoneNumberE164: String,
  val cloudRunProfileRef: String,
  val isDefault: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)

data class DeviceKeyStatus(
  val isReady: Boolean = false,
  val keyAlias: String = "",
  val publicKeyFingerprint: String = "",
  val publicKeyBase64: String = "",
  val errorMessage: String? = null,
)

data class ProductionGateItem(
  val id: Int,
  val title: String,
  val description: String,
  val isVerified: Boolean,
)
