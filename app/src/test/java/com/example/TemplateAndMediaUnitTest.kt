package com.example

import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactImportStatus
import com.example.domain.model.ContactWithFields
import com.example.domain.model.TemplateParameterMapping
import com.example.domain.model.TemplateValidationStatus
import com.example.domain.util.ImageMediaSecurity
import com.example.domain.util.TemplateEngine
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateAndMediaUnitTest {

  @Test
  fun `extracts placeholders in order and compiles Meta positional parameters`() {
    val body = "Hello {Name}, your seat for {Workshop Name} at {College} ({City}) is confirmed. See you, {Name}!"
    val placeholders = TemplateEngine.extractPlaceholders(body)
    assertEquals(listOf("Name", "Workshop Name", "College", "City"), placeholders)

    val availableFields = TemplateEngine.standardContactFieldKeys + listOf("Workshop Name")
    val mappings = TemplateEngine.syncMappings(body, emptyList(), availableFields)
    assertEquals(4, mappings.size)
    assertEquals(1, mappings[0].metaIndex)
    assertEquals("Name", mappings[0].contactFieldKey)
    assertEquals(2, mappings[1].metaIndex)
    assertEquals("Workshop Name", mappings[1].contactFieldKey)

    val compiled = TemplateEngine.compileMetaBody(body, mappings)
    assertEquals(
      "Hello {{1}}, your seat for {{2}} at {{3}} ({{4}}) is confirmed. See you, {{1}}!",
      compiled,
    )
  }

  @Test
  fun `validates Meta rules and flags missing mapping or invalid body`() {
    val availableFields = TemplateEngine.standardContactFieldKeys

    val badMeta =
      TemplateEngine.validateTemplate(
        templateName = "Campus Invite",
        metaTemplateName = "Campus Invite!",
        bodyText = "Hi {Name}, welcome!",
        mappings = listOf(TemplateParameterMapping("Name", 1, "Name")),
        headerMediaRequired = false,
        availableFields = availableFields,
      )
    assertEquals(TemplateValidationStatus.INVALID, badMeta.status)
    assertTrue(badMeta.errors.any { it.contains("lowercase") })

    val tooLong =
      TemplateEngine.validateTemplate(
        templateName = "Long Msg",
        metaTemplateName = "long_msg",
        bodyText = "a".repeat(TemplateEngine.MAX_META_BODY_CHARS + 1),
        mappings = emptyList(),
        headerMediaRequired = false,
        availableFields = availableFields,
      )
    assertEquals(TemplateValidationStatus.INVALID, tooLong.status)

    val needsMapping =
      TemplateEngine.validateTemplate(
        templateName = "Custom Field Invite",
        metaTemplateName = "custom_field_invite",
        bodyText = "Hi {Name}, your booth is {BoothNumber}.",
        mappings =
          listOf(
            TemplateParameterMapping("Name", 1, "Name"),
            TemplateParameterMapping("BoothNumber", 2, "BoothNumber", fallbackValue = ""),
          ),
        headerMediaRequired = false,
        availableFields = availableFields,
      )
    assertEquals(TemplateValidationStatus.NEEDS_MAPPING, needsMapping.status)

    val validWithFallback =
      TemplateEngine.validateTemplate(
        templateName = "Custom Field Invite",
        metaTemplateName = "custom_field_invite",
        bodyText = "Hi {Name}, your booth is {BoothNumber}.",
        mappings =
          listOf(
            TemplateParameterMapping("Name", 1, "Name"),
            TemplateParameterMapping("BoothNumber", 2, "BoothNumber", fallbackValue = "Main Hall"),
          ),
        headerMediaRequired = false,
        availableFields = availableFields,
      )
    assertEquals(TemplateValidationStatus.VALID, validWithFallback.status)
  }

  @Test
  fun `renders per-contact preview and detects missing fields`() {
    val contact =
      ContactWithFields(
        id = 1L,
        name = "Meera Krishnan",
        phoneE164 = "+919876543210",
        rawPhone = "9876543210",
        email = null,
        college = "NIT Trichy",
        city = null,
        state = "Tamil Nadu",
        consentStatus = ConsentStatus.GRANTED,
        isOptedOut = false,
        importStatus = ContactImportStatus.CSV_IMPORTED,
        importedAt = 1_700_000_000_000L,
        customFields = mapOf("Track" to "AI Systems"),
      )

    val mappings =
      listOf(
        TemplateParameterMapping("Name", 1, "Name"),
        TemplateParameterMapping("College", 2, "College"),
        TemplateParameterMapping("City", 3, "City", fallbackValue = ""),
        TemplateParameterMapping("Track", 4, "Track"),
      )

    val preview =
      TemplateEngine.renderForContact(
        bodyText = "Hi {Name} from {College} ({City}), welcome to {Track}!",
        mappings = mappings,
        contact = contact,
      )

    assertEquals(
      "Hi Meera Krishnan from NIT Trichy ([missing:City]), welcome to AI Systems!",
      preview.renderedText,
    )
    assertEquals(listOf("City"), preview.missingPlaceholders)
    assertEquals(listOf("Meera Krishnan", "NIT Trichy", "", "AI Systems"), preview.positionalParameters)
  }

  @Test
  fun `detects JPEG and PNG magic bytes and rejects disguised text`() {
    val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16, 0x4A, 0x46)
    val pngHeader =
      byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)
    val fakeImage = "<html><script>alert(1)</script></html>".toByteArray(StandardCharsets.UTF_8)

    assertEquals("image/jpeg", ImageMediaSecurity.detectVerifiedImageMime(jpegHeader))
    assertEquals("image/png", ImageMediaSecurity.detectVerifiedImageMime(pngHeader))
    assertNull(ImageMediaSecurity.detectVerifiedImageMime(fakeImage))
    assertNull(ImageMediaSecurity.detectVerifiedImageMime(byteArrayOf(1, 2, 3)))

    val shaA = ImageMediaSecurity.sha256Hex(pngHeader)
    val shaB = ImageMediaSecurity.sha256Hex(pngHeader)
    assertEquals(64, shaA.length)
    assertEquals(shaA, shaB)

    val safeName = ImageMediaSecurity.sanitizeDisplayName("../../bad\\path/flyer?promo#1.png")
    assertEquals("flyer_promo_1.png", safeName)
  }
}
