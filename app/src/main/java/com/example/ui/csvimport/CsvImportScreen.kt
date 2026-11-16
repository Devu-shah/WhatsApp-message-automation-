package com.example.ui.csvimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CsvImportPreview
import com.example.domain.model.CsvRowStatus
import com.example.domain.model.ParsedCsvRow
import com.example.ui.components.EmptyStateCard

@Composable
fun CsvImportScreen(
  viewModel: CsvImportViewModel,
  onNavigateContacts: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val openCsvLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importCsvFromUri(context.contentResolver, uri)
      }
    }

  LaunchedEffect(uiState.feedbackMessage) {
    val msg = uiState.feedbackMessage
    if (msg != null) {
      onShowMessage(msg)
      viewModel.clearFeedback()
    }
  }

  Column(
    modifier = modifier.fillMaxSize().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 1140.dp).fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("CSV Import & Column Mapping", style = MaterialTheme.typography.headlineMedium)
          Text(
            text =
              if (uiState.settings.autoApplyDefaultCountryCode)
                "Auto-prefixing local numbers with ${uiState.settings.defaultCountryCode}; CSV '+' prefixes preserved."
              else "Strict mode: every CSV row must contain an explicit '+' country code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (uiState.preview != null) {
            OutlinedButton(onClick = viewModel::clearPreview) {
              Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Reset")
            }
          }
          Button(
            onClick = {
              openCsvLauncher.launch(
                arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")
              )
            },
            modifier = Modifier.testTag("pick_csv_file_button"),
          ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (uiState.preview == null) "Choose CSV File" else "Load Another CSV")
          }
        }
      }

      if (uiState.isParsing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Validating CSV headers and E.164 phone numbers...")
          }
        }
      } else {
        val preview = uiState.preview
        if (preview == null) {
          EmptyStateCard(
            icon = Icons.Outlined.UploadFile,
            title = "Select a CSV contact sheet via Android Document Picker",
            description =
              "Supports up to 15 MB / 25,000 rows. Automatically detects headers, maps Phone/Name/College/City/State + custom columns, validates E.164 numbers, and flags duplicates or opted-out recipients before saving to Room.",
            actionLabel = "Pick CSV File",
            onAction = {
              openCsvLauncher.launch(
                arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")
              )
            },
          )
        } else {
          CsvPreviewWorkspace(
            uiState = uiState,
            preview = preview,
            viewModel = viewModel,
            onNavigateContacts = onNavigateContacts,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsvPreviewWorkspace(
  uiState: CsvImportUiState,
  preview: CsvImportPreview,
  viewModel: CsvImportViewModel,
  onNavigateContacts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedCount = preview.evaluatedRows.count { it.selectedForImport }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Summary & Fallback Switch Bar
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${preview.fileName} • ${preview.evaluatedRows.size} rows",
            style = MaterialTheme.typography.titleMedium,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Auto-apply ${uiState.settings.defaultCountryCode}",
              style = MaterialTheme.typography.labelLarge,
            )
            Switch(
              checked = uiState.settings.autoApplyDefaultCountryCode,
              onCheckedChange = viewModel::toggleAutoApplyDefaultCode,
              modifier = Modifier.testTag("csv_auto_apply_code_switch"),
            )
          }
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SummaryBadge("Valid: ${preview.validCount}", MaterialTheme.colorScheme.tertiaryContainer)
          SummaryBadge(
            "Duplicates: ${preview.duplicateCount}",
            MaterialTheme.colorScheme.secondaryContainer,
          )
          SummaryBadge("Invalid: ${preview.invalidCount}", MaterialTheme.colorScheme.errorContainer)
          SummaryBadge(
            "Suppressed: ${preview.suppressedCount}",
            MaterialTheme.colorScheme.errorContainer,
          )
        }

        uiState.lastCommitResult?.let { result ->
          Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(
                  text =
                    "Saved to Room: ${result.insertedCount} inserted, ${result.updatedCount} updated, ${result.skippedCount} skipped.",
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
              TextButton(onClick = onNavigateContacts) { Text("View Contacts") }
            }
          }
        }
      }
    }

    // Main Scrollable Workspace: Column Mapping + Sample + Filtered Rows
    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        ColumnMappingCard(
          headers = preview.headers,
          preview = preview,
          viewModel = viewModel,
        )
      }

      item {
        SampleCsvRowsCard(headers = preview.headers, sampleRows = preview.sampleRawRows)
      }

      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Update existing contacts on DB duplicates", style = MaterialTheme.typography.bodyMedium)
              Switch(
                checked = uiState.updateExistingDuplicates,
                onCheckedChange = viewModel::setUpdateExistingDuplicates,
                modifier = Modifier.testTag("csv_update_duplicates_switch"),
              )
            }

            TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
              Tab(
                selected = uiState.activeTab == CsvPreviewFilterTab.ALL,
                onClick = { viewModel.setActiveTab(CsvPreviewFilterTab.ALL) },
                text = { Text("All (${preview.evaluatedRows.size})") },
              )
              Tab(
                selected = uiState.activeTab == CsvPreviewFilterTab.VALID,
                onClick = { viewModel.setActiveTab(CsvPreviewFilterTab.VALID) },
                text = { Text("Valid (${preview.validCount})") },
              )
              Tab(
                selected = uiState.activeTab == CsvPreviewFilterTab.DUPLICATES,
                onClick = { viewModel.setActiveTab(CsvPreviewFilterTab.DUPLICATES) },
                text = { Text("Duplicates (${preview.duplicateCount})") },
              )
              Tab(
                selected = uiState.activeTab == CsvPreviewFilterTab.INVALID_OR_SUPPRESSED,
                onClick = { viewModel.setActiveTab(CsvPreviewFilterTab.INVALID_OR_SUPPRESSED) },
                text = { Text("Invalid (${preview.invalidCount + preview.suppressedCount})") },
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { viewModel.selectAllEligible(true) }) {
                Text("Select All Eligible")
              }
              OutlinedButton(onClick = { viewModel.selectAllEligible(false) }) {
                Text("Deselect All")
              }
            }
          }
        }
      }

      val visibleRows =
        preview.evaluatedRows.filter { row ->
          when (uiState.activeTab) {
            CsvPreviewFilterTab.ALL -> true
            CsvPreviewFilterTab.VALID -> row.status == CsvRowStatus.VALID
            CsvPreviewFilterTab.DUPLICATES ->
              row.status == CsvRowStatus.DUPLICATE_IN_FILE ||
                row.status == CsvRowStatus.DUPLICATE_IN_DB
            CsvPreviewFilterTab.INVALID_OR_SUPPRESSED ->
              row.status == CsvRowStatus.MISSING_PHONE ||
                row.status == CsvRowStatus.INVALID_PHONE ||
                row.status == CsvRowStatus.OPTED_OUT_SUPPRESSED
          }
        }

      items(visibleRows, key = { it.rowNumber }) { row ->
        EvaluatedRowCard(
          row = row,
          allowDbDuplicates = uiState.updateExistingDuplicates,
          onToggle = { viewModel.toggleRowSelection(row.rowNumber) },
        )
      }
    }

    // Bottom Selective Commit Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      shape = MaterialTheme.shapes.large,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "$selectedCount rows selected for selective import",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "Invalid, missing-phone, and suppressed rows are excluded automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = viewModel::commitSelectedRows,
          enabled = selectedCount > 0 && !uiState.isCommitting,
          modifier = Modifier.testTag("commit_csv_import_button"),
        ) {
          if (uiState.isCommitting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
          }
          Text("Import $selectedCount Contacts")
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnMappingCard(
  headers: List<String>,
  preview: CsvImportPreview,
  viewModel: CsvImportViewModel,
) {
  val mapping = preview.mapping
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Column Mapping", style = MaterialTheme.typography.titleMedium)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ColumnDropdownSelector(
          label = "Phone Number *",
          headers = headers,
          selectedIndex = mapping.phoneColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(phoneColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
        ColumnDropdownSelector(
          label = "Name",
          headers = headers,
          selectedIndex = mapping.nameColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(nameColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
        ColumnDropdownSelector(
          label = "Email",
          headers = headers,
          selectedIndex = mapping.emailColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(emailColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
        ColumnDropdownSelector(
          label = "College",
          headers = headers,
          selectedIndex = mapping.collegeColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(collegeColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
        ColumnDropdownSelector(
          label = "City",
          headers = headers,
          selectedIndex = mapping.cityColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(cityColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
        ColumnDropdownSelector(
          label = "State",
          headers = headers,
          selectedIndex = mapping.stateColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(stateColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
        ColumnDropdownSelector(
          label = "Consent",
          headers = headers,
          selectedIndex = mapping.consentColumnIndex,
          onSelect = { idx -> viewModel.updateMapping { it.copy(consentColumnIndex = idx) } },
          modifier = Modifier.width(210.dp),
        )
      }

      val mappedSet =
        setOfNotNull(
          mapping.phoneColumnIndex,
          mapping.nameColumnIndex,
          mapping.emailColumnIndex,
          mapping.collegeColumnIndex,
          mapping.cityColumnIndex,
          mapping.stateColumnIndex,
          mapping.consentColumnIndex,
        )
      val unmappedIndices = headers.indices.filter { it !in mappedSet }
      if (unmappedIndices.isNotEmpty()) {
        HorizontalDivider()
        Text(
          text = "Preserve Custom CSV Columns (available as {Placeholders} in Phase 3):",
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          unmappedIndices.forEach { colIdx ->
            FilterChip(
              selected = colIdx in mapping.preservedCustomColumnIndices,
              onClick = { viewModel.togglePreserveCustomColumn(colIdx) },
              label = { Text(headers[colIdx]) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdownSelector(
  label: String,
  headers: List<String>,
  selectedIndex: Int?,
  onSelect: (Int?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedText = selectedIndex?.let { headers.getOrNull(it) } ?: "Not mapped"

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = selectedText,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      singleLine = true,
      modifier =
        Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text("Not mapped") },
        onClick = {
          onSelect(null)
          expanded = false
        },
      )
      headers.forEachIndexed { idx, header ->
        DropdownMenuItem(
          text = { Text(header) },
          onClick = {
            onSelect(idx)
            expanded = false
          },
        )
      }
    }
  }
}

@Composable
private fun SampleCsvRowsCard(headers: List<String>, sampleRows: List<List<String>>) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Raw CSV Preview (First ${sampleRows.size} Rows)", style = MaterialTheme.typography.titleSmall)
      Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(
          modifier =
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp)
        ) {
          headers.forEach { header ->
            Text(
              text = header,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.width(135.dp).padding(horizontal = 8.dp),
            )
          }
        }
        sampleRows.forEach { row ->
          HorizontalDivider()
          Row(modifier = Modifier.padding(vertical = 6.dp)) {
            row.forEach { cell ->
              Text(
                text = cell.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(135.dp).padding(horizontal = 8.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EvaluatedRowCard(
  row: ParsedCsvRow,
  allowDbDuplicates: Boolean,
  onToggle: () -> Unit,
) {
  val selectable =
    row.status == CsvRowStatus.VALID ||
      (allowDbDuplicates && row.status == CsvRowStatus.DUPLICATE_IN_DB)
  val badgeColor =
    when (row.status) {
      CsvRowStatus.VALID -> MaterialTheme.colorScheme.tertiaryContainer
      CsvRowStatus.DUPLICATE_IN_FILE,
      CsvRowStatus.DUPLICATE_IN_DB -> MaterialTheme.colorScheme.secondaryContainer
      CsvRowStatus.MISSING_PHONE,
      CsvRowStatus.INVALID_PHONE,
      CsvRowStatus.OPTED_OUT_SUPPRESSED -> MaterialTheme.colorScheme.errorContainer
    }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Checkbox(
        checked = row.selectedForImport,
        onCheckedChange = { if (selectable) onToggle() },
        enabled = selectable,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "#${row.rowNumber}  •  ${row.resolvedName}",
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text =
            "Raw: ${row.rawPhone.ifEmpty { "—" }}  →  E.164: ${row.normalizedPhoneE164 ?: "Invalid"}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.customFields.isNotEmpty()) {
          Text(
            text = row.customFields.entries.joinToString(" | ") { "${it.key}=${it.value}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Surface(color = badgeColor, shape = RoundedCornerShape(8.dp)) {
        Text(
          text = row.statusReason,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun SummaryBadge(text: String, containerColor: androidx.compose.ui.graphics.Color) {
  Surface(color = containerColor, shape = RoundedCornerShape(8.dp)) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}
