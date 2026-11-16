package com.example.domain.util

import com.example.data.local.db.entity.MessageRecordEntity
import com.example.domain.model.CampaignStatsSummary
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactWithFields
import com.example.domain.model.CsvColumnMapping
import com.example.domain.model.CsvRowStatus
import com.example.domain.model.ParsedCsvRow
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ParsedCsvTable(
  val headers: List<String>,
  val rows: List<List<String>>,
)

/**
 * Hardened RFC-4180 CSV parser & formula-injection-safe CSV exporter.
 * Treats all imported files as untrusted input.
 */
object CsvParserAndSecurity {

  const val MAX_FILE_BYTES: Long = 15L * 1024L * 1024L // 15 MB ceiling
  const val MAX_DATA_ROWS: Int = 25_000
  const val MAX_COLUMNS: Int = 64
  const val MAX_CELL_CHARS: Int = 2_048

  private val formulaTriggers = setOf('=', '+', '-', '@', '\t', '\r')

  fun parseCsvStream(
    inputStream: InputStream,
    reportedSizeBytes: Long? = null,
  ): Result<ParsedCsvTable> {
    if (reportedSizeBytes != null && reportedSizeBytes > MAX_FILE_BYTES) {
      return Result.failure(
        IllegalArgumentException("CSV file exceeds maximum allowed size of 15 MB")
      )
    }

    return runCatching {
      val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
      val rawRecords = parseRfc4180Records(reader)
      if (rawRecords.isEmpty()) {
        throw IllegalArgumentException("CSV file is empty")
      }

      val rawHeaders = rawRecords.first()
      if (rawHeaders.isEmpty() || rawHeaders.all { it.isBlank() }) {
        throw IllegalArgumentException("CSV header row is missing or blank")
      }
      if (rawHeaders.size > MAX_COLUMNS) {
        throw IllegalArgumentException("CSV exceeds maximum supported column count ($MAX_COLUMNS)")
      }

      // Disambiguate duplicate or blank header names safely
      val seenHeaders = mutableMapOf<String, Int>()
      val cleanHeaders =
        rawHeaders.mapIndexed { idx, rawHeader ->
          val base = sanitizeCellInput(rawHeader).ifEmpty { "Column_${idx + 1}" }
          val count = seenHeaders.getOrDefault(base, 0) + 1
          seenHeaders[base] = count
          if (count == 1) base else "${base}_$count"
        }

      val dataRows =
        rawRecords.drop(1).filter { row -> row.any { it.isNotBlank() } }.map { row ->
          val padded =
            List(cleanHeaders.size) { colIdx ->
              sanitizeCellInput(row.getOrElse(colIdx) { "" })
            }
          padded
        }

      if (dataRows.isEmpty()) {
        throw IllegalArgumentException("CSV contains headers but zero data rows")
      }
      if (dataRows.size > MAX_DATA_ROWS) {
        throw IllegalArgumentException(
          "CSV exceeds maximum supported row count of $MAX_DATA_ROWS rows"
        )
      }

      ParsedCsvTable(headers = cleanHeaders, rows = dataRows)
    }
  }

  private fun parseRfc4180Records(reader: BufferedReader): List<List<String>> {
    val records = mutableListOf<List<String>>()
    val currentRecord = mutableListOf<String>()
    val currentCell = StringBuilder()
    var inQuotes = false
    var totalCharsRead = 0L
    val maxTotalChars = MAX_FILE_BYTES // bound total decoded characters as well

    var firstChar = true
    while (true) {
      val intChar = reader.read()
      if (intChar == -1) break
      totalCharsRead++
      if (totalCharsRead > maxTotalChars) {
        throw IllegalArgumentException("CSV content exceeds safe character limit")
      }

      val ch = intChar.toChar()
      if (firstChar) {
        firstChar = false
        if (ch == '\uFEFF') continue // Strip UTF-8 BOM
      }
      if (ch == '\u0000') {
        throw IllegalArgumentException("Binary null bytes detected — file is not a valid CSV")
      }

      if (inQuotes) {
        if (ch == '"') {
          reader.mark(1)
          val next = reader.read()
          if (next == '"'.code) {
            appendBounded(currentCell, '"')
          } else {
            inQuotes = false
            if (next != -1) reader.reset()
          }
        } else {
          appendBounded(currentCell, ch)
        }
      } else {
        when (ch) {
          '"' -> {
            if (currentCell.isEmpty()) {
              inQuotes = true
            } else {
              appendBounded(currentCell, ch)
            }
          }
          ',' -> {
            currentRecord.add(currentCell.toString())
            currentCell.clear()
          }
          '\r' -> {
            reader.mark(1)
            val next = reader.read()
            if (next != '\n'.code && next != -1) {
              reader.reset()
            }
            currentRecord.add(currentCell.toString())
            currentCell.clear()
            records.add(currentRecord.toList())
            currentRecord.clear()
            if (records.size > MAX_DATA_ROWS + 2) {
              throw IllegalArgumentException("CSV exceeds maximum allowed row count ($MAX_DATA_ROWS)")
            }
          }
          '\n' -> {
            currentRecord.add(currentCell.toString())
            currentCell.clear()
            records.add(currentRecord.toList())
            currentRecord.clear()
            if (records.size > MAX_DATA_ROWS + 2) {
              throw IllegalArgumentException("CSV exceeds maximum allowed row count ($MAX_DATA_ROWS)")
            }
          }
          else -> appendBounded(currentCell, ch)
        }
      }
    }

    if (inQuotes) {
      throw IllegalArgumentException("Malformed CSV: unclosed quoted field at end of file")
    }
    if (currentCell.isNotEmpty() || currentRecord.isNotEmpty()) {
      currentRecord.add(currentCell.toString())
      records.add(currentRecord.toList())
    }
    return records
  }

  private fun appendBounded(sb: StringBuilder, ch: Char) {
    if (sb.length < MAX_CELL_CHARS) {
      sb.append(ch)
    }
  }

  fun sanitizeCellInput(raw: String): String {
    // Strip control chars other than standard spaces and collapse newlines inside cells
    val cleaned =
      buildString(raw.length) {
        for (ch in raw) {
          when {
            ch == '\n' || ch == '\r' || ch == '\t' -> append(' ')
            ch.code < 32 || ch.code == 127 -> Unit
            else -> append(ch)
          }
        }
      }
    return cleaned.trim()
  }

  fun autoDetectMapping(headers: List<String>): CsvColumnMapping {
    val normalized = headers.map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }

    fun findIndex(vararg candidates: String): Int? =
      normalized.indexOfFirst { header -> candidates.any { candidate -> header.contains(candidate) } }
        .takeIf { it >= 0 }

    val phoneIdx = findIndex("phone", "mobile", "whatsapp", "contactno", "tel", "cell", "number")
    val nameIdx =
      normalized.indexOfFirst { header ->
        header == "name" ||
          header == "fullname" ||
          header == "studentname" ||
          header == "recipient" ||
          (header.contains("name") && !header.contains("college") && !header.contains("city"))
      }.takeIf { it >= 0 }
    val emailIdx = findIndex("email", "mail")
    val collegeIdx = findIndex("college", "university", "institute", "school", "campus")
    val cityIdx = findIndex("city", "town", "district")
    val stateIdx = findIndex("state", "province", "region")
    val consentIdx = findIndex("consent", "optin", "permission")

    val mappedIndices =
      setOfNotNull(phoneIdx, nameIdx, emailIdx, collegeIdx, cityIdx, stateIdx, consentIdx)
    val customIndices = headers.indices.filter { it !in mappedIndices }.toSet()

    return CsvColumnMapping(
      phoneColumnIndex = phoneIdx,
      nameColumnIndex = nameIdx,
      emailColumnIndex = emailIdx,
      collegeColumnIndex = collegeIdx,
      cityColumnIndex = cityIdx,
      stateColumnIndex = stateIdx,
      consentColumnIndex = consentIdx,
      preservedCustomColumnIndices = customIndices,
    )
  }

  fun evaluateRows(
    table: ParsedCsvTable,
    mapping: CsvColumnMapping,
    defaultCountryCode: String,
    autoApplyDefaultCode: Boolean,
    existingPhonesInDb: Set<String>,
    suppressedPhones: Set<String>,
  ): List<ParsedCsvRow> {
    val seenInCsv = mutableSetOf<String>()

    return table.rows.mapIndexed { idx, cells ->
      val rowNum = idx + 1
      val rawPhone =
        mapping.phoneColumnIndex?.let { cells.getOrNull(it) }.orEmpty().trim()
      val rawName =
        mapping.nameColumnIndex?.let { cells.getOrNull(it) }.orEmpty().trim()
      val resolvedName = rawName.ifEmpty { "Recipient #$rowNum" }
      val email = mapping.emailColumnIndex?.let { cells.getOrNull(it) }?.takeIf { it.isNotBlank() }
      val college =
        mapping.collegeColumnIndex?.let { cells.getOrNull(it) }?.takeIf { it.isNotBlank() }
      val city = mapping.cityColumnIndex?.let { cells.getOrNull(it) }?.takeIf { it.isNotBlank() }
      val state = mapping.stateColumnIndex?.let { cells.getOrNull(it) }?.takeIf { it.isNotBlank() }
      val consentRaw =
        mapping.consentColumnIndex?.let { cells.getOrNull(it) }?.lowercase()?.trim().orEmpty()
      val consentStatus =
        when {
          consentRaw in setOf("revoked", "no", "false", "0", "opted_out", "optout", "stop") ->
            ConsentStatus.REVOKED
          consentRaw in setOf("pending", "unverified") -> ConsentStatus.PENDING
          consentRaw in setOf("granted", "yes", "true", "1", "opted_in", "optin") ->
            ConsentStatus.GRANTED
          else -> ConsentStatus.GRANTED
        }

      val customFields =
        mapping.preservedCustomColumnIndices
          .sorted()
          .mapNotNull { colIdx ->
            val key = table.headers.getOrNull(colIdx) ?: return@mapNotNull null
            val value = cells.getOrNull(colIdx).orEmpty()
            if (value.isNotBlank()) key to value else null
          }
          .toMap()

      if (rawPhone.isEmpty()) {
        ParsedCsvRow(
          rowNumber = rowNum,
          rawCells = cells,
          resolvedName = resolvedName,
          rawPhone = rawPhone,
          normalizedPhoneE164 = null,
          email = email,
          college = college,
          city = city,
          state = state,
          consentStatus = consentStatus,
          customFields = customFields,
          status = CsvRowStatus.MISSING_PHONE,
          statusReason = "Missing phone number",
          selectedForImport = false,
        )
      } else {
        when (
          val normalized =
            PhoneNumberHelper.normalize(rawPhone, defaultCountryCode, autoApplyDefaultCode)
        ) {
          is PhoneNumberHelper.NormalizationResult.Invalid -> {
            ParsedCsvRow(
              rowNumber = rowNum,
              rawCells = cells,
              resolvedName = resolvedName,
              rawPhone = rawPhone,
              normalizedPhoneE164 = null,
              email = email,
              college = college,
              city = city,
              state = state,
              consentStatus = consentStatus,
              customFields = customFields,
              status = CsvRowStatus.INVALID_PHONE,
              statusReason = normalized.reason,
              selectedForImport = false,
            )
          }
          is PhoneNumberHelper.NormalizationResult.Valid -> {
            val e164 = normalized.e164
            when {
              e164 in suppressedPhones -> {
                ParsedCsvRow(
                  rowNumber = rowNum,
                  rawCells = cells,
                  resolvedName = resolvedName,
                  rawPhone = rawPhone,
                  normalizedPhoneE164 = e164,
                  email = email,
                  college = college,
                  city = city,
                  state = state,
                  consentStatus = ConsentStatus.REVOKED,
                  customFields = customFields,
                  status = CsvRowStatus.OPTED_OUT_SUPPRESSED,
                  statusReason = "On local suppression/opt-out list",
                  selectedForImport = false,
                )
              }
              !seenInCsv.add(e164) -> {
                ParsedCsvRow(
                  rowNumber = rowNum,
                  rawCells = cells,
                  resolvedName = resolvedName,
                  rawPhone = rawPhone,
                  normalizedPhoneE164 = e164,
                  email = email,
                  college = college,
                  city = city,
                  state = state,
                  consentStatus = consentStatus,
                  customFields = customFields,
                  status = CsvRowStatus.DUPLICATE_IN_FILE,
                  statusReason = "Duplicate number within CSV",
                  selectedForImport = false,
                )
              }
              e164 in existingPhonesInDb -> {
                ParsedCsvRow(
                  rowNumber = rowNum,
                  rawCells = cells,
                  resolvedName = resolvedName,
                  rawPhone = rawPhone,
                  normalizedPhoneE164 = e164,
                  email = email,
                  college = college,
                  city = city,
                  state = state,
                  consentStatus = consentStatus,
                  customFields = customFields,
                  status = CsvRowStatus.DUPLICATE_IN_DB,
                  statusReason = "Already exists in contacts",
                  selectedForImport = false,
                )
              }
              else -> {
                val note =
                  if (normalized.appliedDefaultCode) "Normalized with $defaultCountryCode"
                  else "Preserved CSV country code"
                ParsedCsvRow(
                  rowNumber = rowNum,
                  rawCells = cells,
                  resolvedName = resolvedName,
                  rawPhone = rawPhone,
                  normalizedPhoneE164 = e164,
                  email = email,
                  college = college,
                  city = city,
                  state = state,
                  consentStatus = consentStatus,
                  customFields = customFields,
                  status = CsvRowStatus.VALID,
                  statusReason = note,
                  selectedForImport = true,
                )
              }
            }
          }
        }
      }
    }
  }

  /**
   * Neutralizes spreadsheet formula injection (=, +, -, @, tab, CR) and quotes per RFC-4180.
   */
  fun sanitizeExportCell(raw: String): String {
    val clean = sanitizeCellInput(raw)
    val safe =
      if (clean.isNotEmpty() && clean.first() in formulaTriggers) {
        "'$clean"
      } else {
        clean
      }
    val escaped = safe.replace("\"", "\"\"")
    return "\"$escaped\""
  }

  fun buildContactsCsv(contacts: List<ContactWithFields>): String {
    val customKeys =
      contacts.flatMap { it.customFields.keys }.distinct().sorted()
    val headers =
      listOf(
        "Contact ID",
        "Name",
        "Phone (E.164)",
        "Email",
        "College",
        "City",
        "State",
        "Consent Status",
        "Opted Out",
      ) + customKeys

    return buildString {
      appendLine(headers.joinToString(",") { sanitizeExportCell(it) })
      contacts.forEach { contact ->
        val row =
          listOf(
            contact.id.toString(),
            contact.name,
            contact.phoneE164,
            contact.email.orEmpty(),
            contact.college.orEmpty(),
            contact.city.orEmpty(),
            contact.state.orEmpty(),
            contact.consentStatus.name,
            if (contact.isOptedOut) "YES" else "NO",
          ) + customKeys.map { key -> contact.customFields[key].orEmpty() }
        appendLine(row.joinToString(",") { sanitizeExportCell(it) })
      }
    }
  }

  fun buildMessageLogsCsv(
    records: List<MessageRecordEntity>,
    campaignNamesById: Map<Long, String> = emptyMap(),
  ): String {
    val iso =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
    val headers =
      listOf(
        "Message ID",
        "Campaign",
        "Recipient Name",
        "Phone (E.164)",
        "Status",
        "Attempts",
        "Permanent Error",
        "Error Code",
        "Error Detail",
        "WhatsApp Message ID",
        "Idempotency Key",
        "Submitted At (UTC)",
        "Delivered At (UTC)",
        "Read At (UTC)",
      )
    return buildString {
      appendLine(headers.joinToString(",") { sanitizeExportCell(it) })
      records.forEach { record ->
        val row =
          listOf(
            record.id.toString(),
            campaignNamesById[record.campaignId] ?: "Campaign #${record.campaignId}",
            record.recipientName,
            record.recipientPhoneE164,
            record.currentStatus.name,
            record.attemptCount.toString(),
            if (record.isPermanentError) "YES" else "NO",
            record.errorCode.orEmpty(),
            record.errorDescription.orEmpty(),
            record.whatsappMessageId.orEmpty(),
            record.idempotencyKey,
            record.submittedAt?.let { iso.format(Date(it)) }.orEmpty(),
            record.deliveredAt?.let { iso.format(Date(it)) }.orEmpty(),
            record.readAt?.let { iso.format(Date(it)) }.orEmpty(),
          )
        appendLine(row.joinToString(",") { sanitizeExportCell(it) })
      }
    }
  }

  fun buildCampaignReportCsv(
    summary: CampaignStatsSummary,
    records: List<MessageRecordEntity>,
  ): String {
    val iso =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
    val campaign = summary.campaign
    return buildString {
      appendLine(listOf("Metric", "Value").joinToString(",") { sanitizeExportCell(it) })
      val metadataRows =
        listOf(
          "Campaign ID" to campaign.id.toString(),
          "Campaign Name" to campaign.name,
          "Mode" to campaign.mode.name,
          "Status" to campaign.status.name,
          "Template" to summary.templateName,
          "Flyer Asset" to (summary.mediaName ?: "None"),
          "Sender Number" to campaign.senderPhoneSnapshot,
          "Total Recipients" to summary.totalRecipients.toString(),
          "Queued" to summary.queuedCount.toString(),
          "Submitted" to summary.submittedCount.toString(),
          "Sent" to summary.sentCount.toString(),
          "Delivered" to summary.deliveredCount.toString(),
          "Read" to summary.readCount.toString(),
          "Failed" to summary.failedCount.toString(),
          "Skipped" to summary.skippedCount.toString(),
          "Delivery Rate (%)" to String.format(Locale.US, "%.2f", summary.deliveryRatePercent),
          "Read Rate (%)" to String.format(Locale.US, "%.2f", summary.readRatePercent),
          "Failure Rate (%)" to String.format(Locale.US, "%.2f", summary.failureRatePercent),
          "Started At (UTC)" to (campaign.startedAt?.let { iso.format(Date(it)) } ?: ""),
          "Completed At (UTC)" to (campaign.completedAt?.let { iso.format(Date(it)) } ?: ""),
        )
      metadataRows.forEach { (k, v) ->
        appendLine(listOf(k, v).joinToString(",") { sanitizeExportCell(it) })
      }
      appendLine()
      append(buildMessageLogsCsv(records, mapOf(campaign.id to campaign.name)))
    }
  }
}
