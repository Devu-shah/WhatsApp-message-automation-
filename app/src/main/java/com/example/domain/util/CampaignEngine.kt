package com.example.domain.util

import com.example.domain.model.AudienceSelectionMode
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactWithFields
import com.example.domain.model.MessageTemplate
import com.example.domain.model.PreDispatchAudit
import com.example.domain.model.RecipientAuditEntry
import com.example.domain.model.RecipientEligibility
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Pure domain logic for pre-dispatch audience auditing, suppression filtering,
 * duplicate phone collapsing, missing-placeholder detection, and idempotency key derivation.
 */
object CampaignEngine {

  fun buildIdempotencyKey(campaignId: Long, contactId: Long, phoneE164: String): String {
    val seed = "reachhub:v1:$campaignId:$contactId:$phoneE164"
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
    val shortHex = digest.take(12).joinToString("") { "%02x".format(it) }
    return "rh_${campaignId}_${contactId}_$shortHex"
  }

  fun evaluatePreDispatchAudit(
    contacts: List<ContactWithFields>,
    suppressedPhones: Set<String>,
    template: MessageTemplate?,
    audienceMode: AudienceSelectionMode,
    audienceFilterQuery: String,
    selectedContactIds: Set<Long>?,
    defaultCountryCode: String,
    autoApplyDefaultCode: Boolean,
  ): PreDispatchAudit {
    val query = audienceFilterQuery.trim().lowercase()
    val filteredCandidates =
      contacts.filter { contact ->
        val matchesSelection = selectedContactIds == null || selectedContactIds.contains(contact.id)
        if (!matchesSelection) return@filter false
        when (audienceMode) {
          AudienceSelectionMode.ALL_ELIGIBLE -> true
          AudienceSelectionMode.CONSENT_GRANTED_ONLY ->
            !contact.isOptedOut && contact.consentStatus == ConsentStatus.GRANTED
          AudienceSelectionMode.FILTER_BY_QUERY ->
            query.isEmpty() ||
              contact.name.lowercase().contains(query) ||
              (contact.city?.lowercase()?.contains(query) == true) ||
              (contact.college?.lowercase()?.contains(query) == true) ||
              (contact.state?.lowercase()?.contains(query) == true) ||
              contact.customFields.values.any { it.lowercase().contains(query) }
        }
      }

    val seenPhones = HashSet<String>()
    val eligible = mutableListOf<ContactWithFields>()
    val excluded = mutableListOf<RecipientAuditEntry>()
    var invalidCount = 0
    var duplicateCount = 0
    var optedOutCount = 0
    var missingPlaceholderCount = 0

    for (contact in filteredCandidates) {
      if (contact.isOptedOut || contact.consentStatus == ConsentStatus.REVOKED || suppressedPhones.contains(contact.phoneE164)) {
        optedOutCount++
        excluded.add(
          RecipientAuditEntry(
            contact = contact,
            eligibility = RecipientEligibility.OPTED_OUT,
            reason = "Opted out / present on suppression list",
          )
        )
        continue
      }

      val normalized =
        PhoneNumberHelper.normalize(contact.phoneE164, defaultCountryCode, autoApplyDefaultCode)
      if (normalized is PhoneNumberHelper.NormalizationResult.Invalid) {
        invalidCount++
        excluded.add(
          RecipientAuditEntry(
            contact = contact,
            eligibility = RecipientEligibility.INVALID,
            reason = normalized.reason,
          )
        )
        continue
      }
      val validE164 = (normalized as PhoneNumberHelper.NormalizationResult.Valid).e164

      if (!seenPhones.add(validE164)) {
        duplicateCount++
        excluded.add(
          RecipientAuditEntry(
            contact = contact,
            eligibility = RecipientEligibility.DUPLICATE,
            reason = "Duplicate E.164 phone in campaign audience",
          )
        )
        continue
      }

      if (template != null) {
        val rendered = TemplateEngine.renderForContact(template.bodyText, template.mappings, contact)
        if (rendered.missingPlaceholders.isNotEmpty()) {
          missingPlaceholderCount++
        }
      }

      eligible.add(contact)
    }

    val sampleRendered =
      if (template != null && eligible.isNotEmpty()) {
        TemplateEngine.renderForContact(template.bodyText, template.mappings, eligible.first()).renderedText
      } else if (template != null) {
        template.bodyText
      } else {
        "Select a template to preview personalized message"
      }

    return PreDispatchAudit(
      totalEvaluated = filteredCandidates.size,
      validEligibleCount = eligible.size,
      invalidPhoneCount = invalidCount,
      duplicatePhoneCount = duplicateCount,
      optedOutSuppressedCount = optedOutCount,
      missingPlaceholderCount = missingPlaceholderCount,
      eligibleRecipients = eligible,
      excludedEntries = excluded,
      sampleRenderedMessage = sampleRendered,
    )
  }
}
