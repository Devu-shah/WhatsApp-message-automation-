package com.example.domain.model

import com.example.data.local.db.entity.CampaignEntity
import com.example.domain.util.PhoneNumberHelper

enum class AudienceSelectionMode(val label: String) {
  ALL_ELIGIBLE("All Eligible Contacts"),
  CONSENT_GRANTED_ONLY("Explicit Consent Granted Only"),
  FILTER_BY_QUERY("Match City / College / Query"),
}

enum class MessageLogFilter(val label: String) {
  ALL("All"),
  QUEUED("Queued"),
  SUBMITTED("Submitted"),
  SENT("Sent"),
  DELIVERED("Delivered"),
  READ("Read"),
  FAILED("Failed (All)"),
  RETRYABLE_FAILED("Retryable Failed"),
  PERMANENT_FAILED("Permanent Failed"),
  SKIPPED("Skipped"),
}

data class RecipientAuditEntry(
  val contact: ContactWithFields,
  val eligibility: RecipientEligibility,
  val reason: String,
)

data class PreDispatchAudit(
  val totalEvaluated: Int,
  val validEligibleCount: Int,
  val invalidPhoneCount: Int,
  val duplicatePhoneCount: Int,
  val optedOutSuppressedCount: Int,
  val missingPlaceholderCount: Int,
  val eligibleRecipients: List<ContactWithFields>,
  val excludedEntries: List<RecipientAuditEntry>,
  val sampleRenderedMessage: String,
)

data class CampaignStatsSummary(
  val campaign: CampaignEntity,
  val templateName: String,
  val mediaName: String?,
  val totalRecipients: Int,
  val queuedCount: Int,
  val submittedCount: Int,
  val sentCount: Int,
  val deliveredCount: Int,
  val readCount: Int,
  val failedCount: Int,
  val retryableFailedCount: Int,
  val permanentFailedCount: Int,
  val skippedCount: Int,
) {
  val processedCount: Int
    get() = submittedCount + sentCount + deliveredCount + readCount + failedCount + skippedCount

  val progressFraction: Float
    get() =
      if (totalRecipients <= 0) 0f
      else (totalRecipients - queuedCount).toFloat() / totalRecipients.toFloat()

  val deliveryRatePercent: Double
    get() =
      if (totalRecipients <= 0) 0.0
      else ((deliveredCount + readCount) * 100.0) / totalRecipients.toDouble()

  val readRatePercent: Double
    get() = if (totalRecipients <= 0) 0.0 else (readCount * 100.0) / totalRecipients.toDouble()

  val failureRatePercent: Double
    get() = if (totalRecipients <= 0) 0.0 else (failedCount * 100.0) / totalRecipients.toDouble()
}

data class ErrorClassification(
  val code: String,
  val isPermanent: Boolean,
  val reasonSummary: String,
)

object RetryPolicyClassifier {
  private val permanentErrorCodes =
    setOf(
      "131026", // Receiver phone number is not on WhatsApp / undeliverable
      "131047", // Re-engagement / outside allowed window
      "131051", // Unsupported message type
      "131031", // Business account locked
      "132000", // Template parameter count mismatch
      "132001", // Template does not exist
      "132007", // Template format policy violation
      "132012", // Template parameter format invalid
      "135000", // Generic invalid parameter
      "INVALID_PHONE",
      "OPTED_OUT",
      "POLICY_BLOCKED",
    )

  private val temporaryErrorCodes =
    setOf(
      "130429", // Cloud API throughput/rate limit reached
      "131000", // Something went wrong on Meta side
      "131016", // Service temporarily unavailable
      "131048", // Spam rate limit hit
      "429",
      "500",
      "502",
      "503",
      "504",
      "TIMEOUT",
      "NETWORK_ERROR",
      "SIM_TEMP_RATE_LIMIT",
    )

  fun classify(errorCode: String?, detail: String? = null): ErrorClassification {
    val cleanCode = errorCode?.trim()?.uppercase().orEmpty().ifEmpty { "UNKNOWN_TEMP" }
    val permanent =
      permanentErrorCodes.contains(cleanCode) ||
        (detail?.contains("not registered", ignoreCase = true) == true) ||
        (detail?.contains("template does not exist", ignoreCase = true) == true)
    val summary =
      when {
        permanent ->
          detail?.takeIf { it.isNotBlank() }
            ?: "Permanent WhatsApp/Meta error ($cleanCode) — excluded from retries."
        temporaryErrorCodes.contains(cleanCode) ->
          detail?.takeIf { it.isNotBlank() }
            ?: "Temporary delivery/rate-limit error ($cleanCode) — eligible for retry."
        else ->
          detail?.takeIf { it.isNotBlank() }
            ?: "Transient error ($cleanCode) — eligible for bounded backoff retry."
      }
    return ErrorClassification(code = cleanCode, isPermanent = permanent, reasonSummary = summary)
  }

  /**
   * Exponential backoff schedule in milliseconds (30s -> 2m -> 10m -> 30m capped).
   */
  fun computeBackoffDelayMs(attemptNumber: Int): Long {
    val normalizedAttempt = attemptNumber.coerceAtLeast(1)
    return when (normalizedAttempt) {
      1 -> 30_000L
      2 -> 120_000L
      3 -> 600_000L
      else -> 1_800_000L
    }
  }

  fun maskedPhone(phoneE164: String): String = PhoneNumberHelper.maskForDisplay(phoneE164)
}
