package com.example

import com.example.domain.util.PhoneNumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberHelperTest {

  @Test
  fun `applies +91 fallback when toggle is ON and number lacks prefix`() {
    val result =
      PhoneNumberHelper.normalize(
        rawInput = "9876543210",
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
      )
    assertTrue(result is PhoneNumberHelper.NormalizationResult.Valid)
    val valid = result as PhoneNumberHelper.NormalizationResult.Valid
    assertEquals("+919876543210", valid.e164)
    assertTrue(valid.appliedDefaultCode)
  }

  @Test
  fun `strips leading trunk zero when applying default calling code`() {
    val result =
      PhoneNumberHelper.normalize(
        rawInput = "09876543210",
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
      )
    val valid = result as PhoneNumberHelper.NormalizationResult.Valid
    assertEquals("+919876543210", valid.e164)
    assertTrue(valid.appliedDefaultCode)
  }

  @Test
  fun `preserves explicit CSV country code regardless of fallback toggle`() {
    val resultOn =
      PhoneNumberHelper.normalize(
        rawInput = "+1 415-555-2671",
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
      )
    val resultOff =
      PhoneNumberHelper.normalize(
        rawInput = "+1 415-555-2671",
        defaultCountryCode = "+91",
        autoApplyDefaultCode = false,
      )
    val validOn = resultOn as PhoneNumberHelper.NormalizationResult.Valid
    val validOff = resultOff as PhoneNumberHelper.NormalizationResult.Valid
    assertEquals("+14155552671", validOn.e164)
    assertFalse(validOn.appliedDefaultCode)
    assertEquals("+14155552671", validOff.e164)
    assertFalse(validOff.appliedDefaultCode)
  }

  @Test
  fun `rejects number without country code when auto-apply toggle is OFF`() {
    val result =
      PhoneNumberHelper.normalize(
        rawInput = "9876543210",
        defaultCountryCode = "+91",
        autoApplyDefaultCode = false,
      )
    assertTrue(result is PhoneNumberHelper.NormalizationResult.Invalid)
  }

  @Test
  fun `supports custom user-configured calling code`() {
    val result =
      PhoneNumberHelper.normalize(
        rawInput = "7911123456",
        defaultCountryCode = "+44",
        autoApplyDefaultCode = true,
      )
    val valid = result as PhoneNumberHelper.NormalizationResult.Valid
    assertEquals("+447911123456", valid.e164)
  }

  @Test
  fun `masks middle digits safely for UI display`() {
    assertEquals("+919••••210", PhoneNumberHelper.maskForDisplay("+919876543210"))
  }
}
