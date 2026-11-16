package com.example

import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactWithFields
import com.example.domain.model.CsvRowStatus
import com.example.domain.util.CsvParserAndSecurity
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserAndSecurityTest {

  @Test
  fun `parses UTF-8 BOM quoted CSV and auto-detects standard plus custom columns`() {
    val csv =
      "\uFEFFName,Mobile Number,Email,College,City,State,Branch\n" +
        "\"Riya, Sharma\",9876543210,riya@example.edu,\"NIT Surat\",Surat,Gujarat,CSE\n" +
        "Alex Chen,+14155552671,alex@example.com,Berkeley,Berkeley,CA,ECE\n"

    val result =
      CsvParserAndSecurity.parseCsvStream(
        ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
      )
    assertTrue(result.isSuccess)
    val table = result.getOrThrow()
    assertEquals(7, table.headers.size)
    assertEquals("Name", table.headers[0])
    assertEquals("Riya, Sharma", table.rows[0][0])

    val mapping = CsvParserAndSecurity.autoDetectMapping(table.headers)
    assertEquals(0, mapping.nameColumnIndex)
    assertEquals(1, mapping.phoneColumnIndex)
    assertEquals(2, mapping.emailColumnIndex)
    assertEquals(3, mapping.collegeColumnIndex)
    assertEquals(4, mapping.cityColumnIndex)
    assertEquals(5, mapping.stateColumnIndex)
    assertTrue(6 in mapping.preservedCustomColumnIndices)
  }

  @Test
  fun `rejects oversized CSV and binary null bytes`() {
    val oversized =
      CsvParserAndSecurity.parseCsvStream(
        ByteArrayInputStream("Name,Phone\nA,1".toByteArray()),
        reportedSizeBytes = CsvParserAndSecurity.MAX_FILE_BYTES + 1,
      )
    assertTrue(oversized.isFailure)

    val nullBytes =
      CsvParserAndSecurity.parseCsvStream(
        ByteArrayInputStream("Name,Phone\nA,\u0000123".toByteArray())
      )
    assertTrue(nullBytes.isFailure)
  }

  @Test
  fun `evaluates duplicates missing invalid suppressed and respects +91 toggle`() {
    val csv =
      "Name,Phone,Branch\n" +
        "Valid Local,9876543210,CSE\n" +
        "Valid US,+14155552671,AI\n" +
        "Duplicate Local,09876543210,CSE\n" +
        "Already In DB,9123456789,ME\n" +
        "Suppressed,9999988888,IT\n" +
        "Missing Phone,,ECE\n" +
        "Invalid Phone,123,Civil\n"

    val table =
      CsvParserAndSecurity.parseCsvStream(
          ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        )
        .getOrThrow()
    val mapping = CsvParserAndSecurity.autoDetectMapping(table.headers)

    val rowsWithToggleOn =
      CsvParserAndSecurity.evaluateRows(
        table = table,
        mapping = mapping,
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
        existingPhonesInDb = setOf("+919123456789"),
        suppressedPhones = setOf("+919999988888"),
      )

    assertEquals(CsvRowStatus.VALID, rowsWithToggleOn[0].status)
    assertEquals("+919876543210", rowsWithToggleOn[0].normalizedPhoneE164)
    assertEquals("CSE", rowsWithToggleOn[0].customFields["Branch"])
    assertEquals(CsvRowStatus.VALID, rowsWithToggleOn[1].status)
    assertEquals("+14155552671", rowsWithToggleOn[1].normalizedPhoneE164)
    assertEquals(CsvRowStatus.DUPLICATE_IN_FILE, rowsWithToggleOn[2].status)
    assertEquals(CsvRowStatus.DUPLICATE_IN_DB, rowsWithToggleOn[3].status)
    assertEquals(CsvRowStatus.OPTED_OUT_SUPPRESSED, rowsWithToggleOn[4].status)
    assertEquals(CsvRowStatus.MISSING_PHONE, rowsWithToggleOn[5].status)
    assertEquals(CsvRowStatus.INVALID_PHONE, rowsWithToggleOn[6].status)
    assertTrue(rowsWithToggleOn[0].selectedForImport)
    assertFalse(rowsWithToggleOn[4].selectedForImport)

    // With toggle OFF, local 9876543210 is invalid while +14155552671 remains valid
    val rowsWithToggleOff =
      CsvParserAndSecurity.evaluateRows(
        table = table,
        mapping = mapping,
        defaultCountryCode = "+91",
        autoApplyDefaultCode = false,
        existingPhonesInDb = emptySet(),
        suppressedPhones = emptySet(),
      )
    assertEquals(CsvRowStatus.INVALID_PHONE, rowsWithToggleOff[0].status)
    assertEquals(CsvRowStatus.VALID, rowsWithToggleOff[1].status)
  }

  @Test
  fun `sanitizes formula injection triggers in exported CSV`() {
    val exported =
      CsvParserAndSecurity.buildContactsCsv(
        listOf(
          ContactWithFields(
            id = 7,
            name = "=CMD(\"calc\")",
            phoneE164 = "+919876543210",
            rawPhone = "9876543210",
            college = "@SUM(A1:A2)",
            consentStatus = ConsentStatus.GRANTED,
            customFields = mapOf("Note" to "-1+1"),
          )
        )
      )

    assertTrue(exported.contains("\"'=CMD(\"\"calc\"\")\""))
    assertTrue(exported.contains("\"'+919876543210\""))
    assertTrue(exported.contains("\"'@SUM(A1:A2)\""))
    assertTrue(exported.contains("\"'-1+1\""))
  }
}
