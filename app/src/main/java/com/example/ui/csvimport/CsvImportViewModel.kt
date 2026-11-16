package com.example.ui.csvimport

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ContactRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.CsvColumnMapping
import com.example.domain.model.CsvCommitResult
import com.example.domain.model.CsvImportPreview
import com.example.domain.model.CsvRowStatus
import com.example.domain.model.OperatorSettings
import com.example.domain.util.CsvParserAndSecurity
import com.example.domain.util.ParsedCsvTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CsvPreviewFilterTab {
  ALL,
  VALID,
  DUPLICATES,
  INVALID_OR_SUPPRESSED,
}

data class CsvImportUiState(
  val isParsing: Boolean = false,
  val isCommitting: Boolean = false,
  val preview: CsvImportPreview? = null,
  val activeTab: CsvPreviewFilterTab = CsvPreviewFilterTab.ALL,
  val updateExistingDuplicates: Boolean = false,
  val settings: OperatorSettings = OperatorSettings(),
  val lastCommitResult: CsvCommitResult? = null,
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
)

class CsvImportViewModel(
  private val contactRepository: ContactRepository,
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(CsvImportUiState())
  val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

  private var currentTable: ParsedCsvTable? = null
  private var currentFileName: String = ""
  private var currentFileSize: Long = 0L
  private var existingDbPhones: Set<String> = emptySet()
  private var suppressedPhones: Set<String> = emptySet()

  init {
    viewModelScope.launch {
      settingsRepository.settings.collect { latestSettings ->
        val previous = _uiState.value.settings
        _uiState.update { it.copy(settings = latestSettings) }
        if (
          currentTable != null &&
            (previous.defaultCountryCode != latestSettings.defaultCountryCode ||
              previous.autoApplyDefaultCountryCode != latestSettings.autoApplyDefaultCountryCode)
        ) {
          reEvaluateWithMapping(_uiState.value.preview?.mapping)
        }
      }
    }
  }

  fun importCsvFromUri(contentResolver: ContentResolver, uri: Uri) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isParsing = true,
          lastCommitResult = null,
          feedbackMessage = null,
          isError = false,
        )
      }

      val (fileName, fileSize) = queryFileMetadata(contentResolver, uri)
      if (fileSize != null && fileSize > CsvParserAndSecurity.MAX_FILE_BYTES) {
        _uiState.update {
          it.copy(
            isParsing = false,
            feedbackMessage = "Selected file exceeds 15 MB safety limit",
            isError = true,
          )
        }
        return@launch
      }

      val parseResult =
        withContext(Dispatchers.IO) {
          contentResolver.openInputStream(uri)?.use { stream ->
            CsvParserAndSecurity.parseCsvStream(stream, fileSize)
          } ?: Result.failure(IllegalArgumentException("Unable to read selected CSV file"))
        }

      parseResult
        .onSuccess { table ->
          val (existing, suppressed) = contactRepository.getExistingAndSuppressedPhones()
          currentTable = table
          currentFileName = fileName
          currentFileSize = fileSize ?: 0L
          existingDbPhones = existing
          suppressedPhones = suppressed

          val detectedMapping = CsvParserAndSecurity.autoDetectMapping(table.headers)
          val settings = settingsRepository.settings.first()
          val preview =
            buildPreview(
              table = table,
              fileName = fileName,
              fileSize = fileSize ?: 0L,
              mapping = detectedMapping,
              settings = settings,
            )
          _uiState.update {
            it.copy(
              isParsing = false,
              preview = preview,
              activeTab = CsvPreviewFilterTab.ALL,
              feedbackMessage = "Parsed ${table.rows.size} rows from $fileName",
              isError = false,
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isParsing = false,
              feedbackMessage = err.message ?: "Failed to parse CSV",
              isError = true,
            )
          }
        }
    }
  }

  fun updateMapping(transform: (CsvColumnMapping) -> CsvColumnMapping) {
    val currentMapping = _uiState.value.preview?.mapping ?: return
    reEvaluateWithMapping(transform(currentMapping))
  }

  fun togglePreserveCustomColumn(columnIndex: Int) {
    updateMapping { mapping ->
      val updated = mapping.preservedCustomColumnIndices.toMutableSet()
      if (!updated.add(columnIndex)) {
        updated.remove(columnIndex)
      }
      mapping.copy(preservedCustomColumnIndices = updated)
    }
  }

  fun toggleAutoApplyDefaultCode(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.updateAutoApplyDefaultCode(enabled) }
  }

  fun setActiveTab(tab: CsvPreviewFilterTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun setUpdateExistingDuplicates(enabled: Boolean) {
    _uiState.update { state ->
      val currentPreview = state.preview ?: return@update state
      val updatedRows =
        currentPreview.evaluatedRows.map { row ->
          when (row.status) {
            CsvRowStatus.DUPLICATE_IN_DB -> row.copy(selectedForImport = enabled)
            else -> row
          }
        }
      state.copy(
        updateExistingDuplicates = enabled,
        preview = currentPreview.copy(evaluatedRows = updatedRows),
      )
    }
  }

  fun toggleRowSelection(rowNumber: Int) {
    _uiState.update { state ->
      val currentPreview = state.preview ?: return@update state
      val updatedRows =
        currentPreview.evaluatedRows.map { row ->
          if (row.rowNumber == rowNumber && isRowSelectable(row.status, state.updateExistingDuplicates)) {
            row.copy(selectedForImport = !row.selectedForImport)
          } else {
            row
          }
        }
      state.copy(preview = currentPreview.copy(evaluatedRows = updatedRows))
    }
  }

  fun selectAllEligible(selected: Boolean) {
    _uiState.update { state ->
      val currentPreview = state.preview ?: return@update state
      val updatedRows =
        currentPreview.evaluatedRows.map { row ->
          if (isRowSelectable(row.status, state.updateExistingDuplicates)) {
            row.copy(selectedForImport = selected)
          } else {
            row
          }
        }
      state.copy(preview = currentPreview.copy(evaluatedRows = updatedRows))
    }
  }

  fun commitSelectedRows() {
    val state = _uiState.value
    val preview = state.preview ?: return
    val selectedRows = preview.evaluatedRows.filter { it.selectedForImport }
    if (selectedRows.isEmpty()) {
      _uiState.update {
        it.copy(feedbackMessage = "Select at least one valid row to import", isError = true)
      }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isCommitting = true) }
      val result =
        contactRepository.commitCsvImport(
          selectedRows = selectedRows,
          updateExistingDuplicates = state.updateExistingDuplicates,
        )
      val (existing, suppressed) = contactRepository.getExistingAndSuppressedPhones()
      existingDbPhones = existing
      suppressedPhones = suppressed

      val refreshedPreview =
        currentTable?.let { table ->
          buildPreview(
            table = table,
            fileName = currentFileName,
            fileSize = currentFileSize,
            mapping = preview.mapping,
            settings = _uiState.value.settings,
          )
        }

      _uiState.update {
        it.copy(
          isCommitting = false,
          preview = refreshedPreview,
          lastCommitResult = result,
          feedbackMessage =
            "Imported ${result.insertedCount} new, updated ${result.updatedCount}, skipped ${result.skippedCount}",
          isError = false,
        )
      }
    }
  }

  fun clearPreview() {
    currentTable = null
    currentFileName = ""
    currentFileSize = 0L
    _uiState.update {
      it.copy(preview = null, lastCommitResult = null, feedbackMessage = null, isError = false)
    }
  }

  fun clearFeedback() {
    _uiState.update { it.copy(feedbackMessage = null) }
  }

  private fun reEvaluateWithMapping(mapping: CsvColumnMapping?) {
    val table = currentTable ?: return
    val targetMapping = mapping ?: return
    val updatedPreview =
      buildPreview(
        table = table,
        fileName = currentFileName,
        fileSize = currentFileSize,
        mapping = targetMapping,
        settings = _uiState.value.settings,
      )
    _uiState.update { it.copy(preview = updatedPreview) }
  }

  private fun buildPreview(
    table: ParsedCsvTable,
    fileName: String,
    fileSize: Long,
    mapping: CsvColumnMapping,
    settings: OperatorSettings,
  ): CsvImportPreview {
    val evaluated =
      CsvParserAndSecurity.evaluateRows(
        table = table,
        mapping = mapping,
        defaultCountryCode = settings.defaultCountryCode,
        autoApplyDefaultCode = settings.autoApplyDefaultCountryCode,
        existingPhonesInDb = existingDbPhones,
        suppressedPhones = suppressedPhones,
      )
    val updateDuplicates = _uiState.value.updateExistingDuplicates
    val adjusted =
      if (updateDuplicates) {
        evaluated.map { row ->
          if (row.status == CsvRowStatus.DUPLICATE_IN_DB) row.copy(selectedForImport = true)
          else row
        }
      } else {
        evaluated
      }
    return CsvImportPreview(
      fileName = fileName,
      fileSizeBytes = fileSize,
      headers = table.headers,
      sampleRawRows = table.rows.take(5),
      mapping = mapping,
      evaluatedRows = adjusted,
      validCount = adjusted.count { it.status == CsvRowStatus.VALID },
      duplicateCount =
        adjusted.count {
          it.status == CsvRowStatus.DUPLICATE_IN_FILE ||
            it.status == CsvRowStatus.DUPLICATE_IN_DB
        },
      invalidCount =
        adjusted.count {
          it.status == CsvRowStatus.MISSING_PHONE || it.status == CsvRowStatus.INVALID_PHONE
        },
      suppressedCount = adjusted.count { it.status == CsvRowStatus.OPTED_OUT_SUPPRESSED },
    )
  }

  private fun isRowSelectable(status: CsvRowStatus, allowDbDuplicates: Boolean): Boolean =
    status == CsvRowStatus.VALID || (allowDbDuplicates && status == CsvRowStatus.DUPLICATE_IN_DB)

  private suspend fun queryFileMetadata(
    contentResolver: ContentResolver,
    uri: Uri,
  ): Pair<String, Long?> =
    withContext(Dispatchers.IO) {
      var name = "contacts.csv"
      var size: Long? = null
      contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
          if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
        }
      }
      name to size
    }

  class Factory(
    private val contactRepository: ContactRepository,
    private val settingsRepository: SettingsRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CsvImportViewModel(contactRepository, settingsRepository) as T
  }
}
