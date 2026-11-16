package com.example.domain.model

enum class ContactImportStatus(val label: String) {
  CSV_IMPORTED("CSV Imported"),
  CSV_UPDATED("CSV Updated"),
  MANUAL("Manual Entry"),
}

data class ContactWithFields(
  val id: Long = 0,
  val name: String,
  val phoneE164: String,
  val rawPhone: String,
  val email: String? = null,
  val college: String? = null,
  val city: String? = null,
  val state: String? = null,
  val consentStatus: ConsentStatus = ConsentStatus.GRANTED,
  val isOptedOut: Boolean = false,
  val importStatus: ContactImportStatus = ContactImportStatus.MANUAL,
  val importedAt: Long = System.currentTimeMillis(),
  val customFields: Map<String, String> = emptyMap(),
)

data class SuppressionRecord(
  val phoneE164: String,
  val reason: String,
  val source: String,
  val optedOutAt: Long,
)

enum class ContactFilter {
  ALL,
  ELIGIBLE,
  OPTED_OUT,
  CSV_IMPORTED,
  MANUAL,
  CONSENT_GRANTED,
  CONSENT_PENDING_OR_REVOKED,
}

data class CsvColumnMapping(
  val phoneColumnIndex: Int?,
  val nameColumnIndex: Int?,
  val emailColumnIndex: Int? = null,
  val collegeColumnIndex: Int? = null,
  val cityColumnIndex: Int? = null,
  val stateColumnIndex: Int? = null,
  val consentColumnIndex: Int? = null,
  val preservedCustomColumnIndices: Set<Int> = emptySet(),
)

enum class CsvRowStatus {
  VALID,
  DUPLICATE_IN_FILE,
  DUPLICATE_IN_DB,
  MISSING_PHONE,
  INVALID_PHONE,
  OPTED_OUT_SUPPRESSED,
}

data class ParsedCsvRow(
  val rowNumber: Int,
  val rawCells: List<String>,
  val resolvedName: String,
  val rawPhone: String,
  val normalizedPhoneE164: String?,
  val email: String?,
  val college: String?,
  val city: String?,
  val state: String?,
  val consentStatus: ConsentStatus,
  val customFields: Map<String, String>,
  val status: CsvRowStatus,
  val statusReason: String,
  val selectedForImport: Boolean,
)

data class CsvImportPreview(
  val fileName: String,
  val fileSizeBytes: Long,
  val headers: List<String>,
  val sampleRawRows: List<List<String>>,
  val mapping: CsvColumnMapping,
  val evaluatedRows: List<ParsedCsvRow>,
  val validCount: Int,
  val duplicateCount: Int,
  val invalidCount: Int,
  val suppressedCount: Int,
)

data class CsvCommitResult(
  val insertedCount: Int,
  val updatedCount: Int,
  val skippedCount: Int,
)
