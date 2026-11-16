package com.example.domain.util

/**
 * Validates and normalizes phone numbers into E.164 format while honoring the operator's
 * configurable default country calling code (e.g., +91) and on/off auto-apply toggle.
 */
object PhoneNumberHelper {

  sealed class NormalizationResult {
    data class Valid(val e164: String, val appliedDefaultCode: Boolean) : NormalizationResult()

    data class Invalid(val reason: String) : NormalizationResult()
  }

  private val e164Regex = Regex("^\\+[1-9]\\d{6,14}$")
  private val callingCodeRegex = Regex("^\\+[1-9]\\d{0,3}$")

  fun isValidCallingCode(code: String): Boolean = callingCodeRegex.matches(code.trim())

  fun normalize(
    rawInput: String,
    defaultCountryCode: String,
    autoApplyDefaultCode: Boolean,
  ): NormalizationResult {
    val rawTrimmed = rawInput.trim()
    val trimmed =
      if (rawTrimmed.startsWith("'+")) rawTrimmed.removePrefix("'") else rawTrimmed
    if (trimmed.isEmpty()) {
      return NormalizationResult.Invalid("Phone number is empty")
    }

    // Preserve explicit international prefix from CSV or manual input regardless of toggle.
    if (trimmed.startsWith("+") || trimmed.startsWith("00")) {
      val digitsOnly = trimmed.replace(Regex("[^\\d]"), "")
      val normalizedDigits =
        if (trimmed.startsWith("00") && digitsOnly.startsWith("00")) {
          digitsOnly.removePrefix("00")
        } else {
          digitsOnly
        }
      val candidate = "+$normalizedDigits"
      return if (e164Regex.matches(candidate)) {
        NormalizationResult.Valid(e164 = candidate, appliedDefaultCode = false)
      } else {
        NormalizationResult.Invalid("Invalid E.164 length or format")
      }
    }

    val digitsOnly = trimmed.replace(Regex("[^\\d]"), "")
    if (digitsOnly.isEmpty()) {
      return NormalizationResult.Invalid("No digits found in phone number")
    }

    if (!autoApplyDefaultCode) {
      return NormalizationResult.Invalid(
        "Missing '+' country calling code (auto-apply default is OFF)"
      )
    }

    val cleanCode = defaultCountryCode.trim()
    if (!isValidCallingCode(cleanCode)) {
      return NormalizationResult.Invalid("Configured default calling code '$cleanCode' is invalid")
    }

    // Strip a single leading trunk '0' commonly seen in national numbers (e.g., 09876543210 -> 9876543210)
    val nationalDigits = digitsOnly.removePrefix("0")
    val candidate = "$cleanCode$nationalDigits"
    return if (e164Regex.matches(candidate)) {
      NormalizationResult.Valid(e164 = candidate, appliedDefaultCode = true)
    } else {
      NormalizationResult.Invalid("Normalized number '$candidate' does not match E.164 rules")
    }
  }

  /**
   * Masks middle digits for privacy-safe UI display and diagnostic logs.
   */
  fun maskForDisplay(phoneE164: String): String {
    if (phoneE164.length <= 6) return "****"
    val prefix = phoneE164.take(4)
    val suffix = phoneE164.takeLast(3)
    return "$prefix••••$suffix"
  }
}
