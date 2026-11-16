package com.example.ui.contacts

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactFilter
import com.example.domain.model.ContactWithFields
import com.example.domain.model.SuppressionRecord
import com.example.ui.components.EmptyStateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactsScreen(
  viewModel: ContactsViewModel,
  onNavigateCsvImport: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var activeTab by remember { mutableIntStateOf(0) } // 0 = Contacts, 1 = Suppression List
  var editingContact by remember { mutableStateOf<ContactWithFields?>(null) }
  var showAddEditDialog by remember { mutableStateOf(false) }
  var contactPendingDelete by remember { mutableStateOf<ContactWithFields?>(null) }
  var compactDetailContact by remember { mutableStateOf<ContactWithFields?>(null) }
  var showAddSuppressionDialog by remember { mutableStateOf(false) }

  val exportCsvLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        viewModel.exportFilteredToUri(context.contentResolver, uri)
      }
    }

  LaunchedEffect(uiState.feedbackMessage) {
    val msg = uiState.feedbackMessage
    if (msg != null) {
      onShowMessage(msg)
      viewModel.clearFeedback()
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isSplitPane = maxWidth >= 680.dp

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Header + Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Contacts & Suppression", style = MaterialTheme.typography.headlineMedium)
          Text(
            text =
              "${uiState.allContactsCount} total  •  ${uiState.eligibleCount} eligible  •  ${uiState.optedOutCount} suppressed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { exportCsvLauncher.launch("reachhub_contacts.csv") },
            modifier = Modifier.testTag("export_contacts_button"),
          ) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export CSV")
          }
          OutlinedButton(
            onClick = onNavigateCsvImport,
            modifier = Modifier.testTag("contacts_import_csv_button"),
          ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import CSV")
          }
          Button(
            onClick = {
              editingContact = null
              showAddEditDialog = true
            },
            modifier = Modifier.testTag("add_contact_button"),
          ) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add")
          }
        }
      }

      TabRow(selectedTabIndex = activeTab) {
        Tab(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          text = { Text("Contacts (${uiState.allContactsCount})") },
          modifier = Modifier.testTag("tab_contacts_directory"),
        )
        Tab(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          text = { Text("Suppression List (${uiState.optedOutCount})") },
          modifier = Modifier.testTag("tab_suppression_list"),
        )
      }

      if (activeTab == 0) {
        // Search & Filters
        OutlinedTextField(
          value = uiState.searchQuery,
          onValueChange = viewModel::updateSearchQuery,
          placeholder = { Text("Search by name, E.164 phone, college, city, or custom field") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            if (uiState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("contacts_search_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ContactFilter.entries.forEach { filter ->
            val label =
              when (filter) {
                ContactFilter.ALL -> "All"
                ContactFilter.ELIGIBLE -> "Eligible"
                ContactFilter.OPTED_OUT -> "Opted Out"
                ContactFilter.CSV_IMPORTED -> "CSV Imported"
                ContactFilter.MANUAL -> "Manual Entry"
                ContactFilter.CONSENT_GRANTED -> "Consent Granted"
                ContactFilter.CONSENT_PENDING_OR_REVOKED -> "Pending / Revoked"
              }
            FilterChip(
              selected = uiState.activeFilter == filter,
              onClick = { viewModel.updateFilter(filter) },
              label = { Text(label) },
            )
          }
        }

        if (uiState.filteredContacts.isEmpty()) {
          EmptyStateCard(
            icon = Icons.Outlined.PersonAdd,
            title =
              if (uiState.allContactsCount == 0) "No contacts imported yet"
              else "No contacts match your search or filter",
            description =
              if (uiState.allContactsCount == 0)
                "Import a CSV contact list via Storage Access Framework or add a contact manually."
              else "Try clearing the search query or switching the active filter.",
            actionLabel = if (uiState.allContactsCount == 0) "Import CSV Contacts" else null,
            onAction = if (uiState.allContactsCount == 0) onNavigateCsvImport else null,
          )
        } else if (isSplitPane) {
          Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            LazyColumn(
              modifier = Modifier.weight(1f).fillMaxHeight(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(uiState.filteredContacts, key = { it.id }) { contact ->
                ContactRowCard(
                  contact = contact,
                  selected = uiState.selectedContact?.id == contact.id,
                  onClick = { viewModel.selectContact(contact.id) },
                )
              }
            }
            Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
              uiState.selectedContact?.let { selected ->
                ContactDetailPane(
                  contact = selected,
                  onEdit = {
                    editingContact = selected
                    showAddEditDialog = true
                  },
                  onToggleOptOut = { optedOut -> viewModel.toggleOptOut(selected, optedOut) },
                  onDelete = { contactPendingDelete = selected },
                )
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(uiState.filteredContacts, key = { it.id }) { contact ->
              ContactRowCard(
                contact = contact,
                selected = false,
                onClick = {
                  viewModel.selectContact(contact.id)
                  compactDetailContact = contact
                },
              )
            }
          }
        }
      } else {
        // Suppression List Tab
        SuppressionListPane(
          suppressions = uiState.suppressionList,
          onAddSuppression = { showAddSuppressionDialog = true },
          onRemoveSuppression = viewModel::removeSuppression,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  if (showAddEditDialog) {
    AddOrEditContactDialog(
      initial = editingContact,
      defaultCode = uiState.settings.defaultCountryCode,
      onDismiss = { showAddEditDialog = false },
      onSave = { name, phone, email, college, city, state, consent, optedOut, customFields ->
        viewModel.saveContact(
          id = editingContact?.id ?: 0L,
          name = name,
          rawPhone = phone,
          email = email,
          college = college,
          city = city,
          state = state,
          consentStatus = consent,
          isOptedOut = optedOut,
          customFields = customFields,
        )
        showAddEditDialog = false
      },
    )
  }

  compactDetailContact?.let { contact ->
    AlertDialog(
      onDismissRequest = { compactDetailContact = null },
      title = { Text(contact.name) },
      text = {
        ContactDetailContent(contact = contact, modifier = Modifier.verticalScroll(rememberScrollState()))
      },
      confirmButton = {
        Button(
          onClick = {
            compactDetailContact = null
            editingContact = contact
            showAddEditDialog = true
          }
        ) {
          Text("Edit")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TextButton(
            onClick = {
              viewModel.toggleOptOut(contact, !contact.isOptedOut)
              compactDetailContact = null
            }
          ) {
            Text(if (contact.isOptedOut) "Restore" else "Opt Out")
          }
          TextButton(
            onClick = {
              compactDetailContact = null
              contactPendingDelete = contact
            }
          ) {
            Text("Delete")
          }
        }
      },
    )
  }

  contactPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { contactPendingDelete = null },
      title = { Text("Delete Contact?") },
      text = {
        Text(
          "Permanently delete ${target.name} (${target.phoneE164}) and all associated custom CSV fields from ReachHub?"
        )
      },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            viewModel.deleteContact(target.id)
            contactPendingDelete = null
          },
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { contactPendingDelete = null }) { Text("Cancel") } },
    )
  }

  if (showAddSuppressionDialog) {
    AddSuppressionDialog(
      defaultCode = uiState.settings.defaultCountryCode,
      onDismiss = { showAddSuppressionDialog = false },
      onSave = { phone, reason ->
        viewModel.addManualSuppression(phone, reason)
        showAddSuppressionDialog = false
      },
    )
  }
}

@Composable
private fun ContactRowCard(
  contact: ContactWithFields,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surface
      ),
    border =
      BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(
              if (contact.isOptedOut) MaterialTheme.colorScheme.errorContainer
              else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = contact.name.take(1).uppercase(),
          style = MaterialTheme.typography.titleMedium,
          color =
            if (contact.isOptedOut) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = contact.name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = contact.phoneE164,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val location =
          listOfNotNull(contact.college, contact.city, contact.state).joinToString(" • ")
        if (location.isNotEmpty()) {
          Text(
            text = location,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Surface(
          color =
            if (contact.isOptedOut) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = if (contact.isOptedOut) "OPTED OUT" else contact.consentStatus.name,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (contact.isOptedOut) MaterialTheme.colorScheme.onErrorContainer
              else MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = contact.importStatus.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ContactDetailPane(
  contact: ContactWithFields,
  onEdit: () -> Unit,
  onToggleOptOut: (Boolean) -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxSize(),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(contact.name, style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Contact ID #${contact.id} • ${contact.phoneE164}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("edit_contact_button")) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Edit")
          }
          IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_contact_button")) {
            Icon(
              Icons.Outlined.DeleteOutline,
              contentDescription = "Delete contact",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      HorizontalDivider()

      ContactDetailContent(contact = contact)

      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Suppression / Opt-Out Lock", style = MaterialTheme.typography.titleSmall)
          Text(
            text =
              if (contact.isOptedOut)
                "Blocked from all future campaigns via the suppression list."
              else "Eligible for campaigns when consent is granted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = contact.isOptedOut,
          onCheckedChange = onToggleOptOut,
          modifier = Modifier.testTag("contact_optout_switch"),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDetailContent(contact: ContactWithFields, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    DetailFieldRow("Normalized Phone (E.164)", contact.phoneE164)
    DetailFieldRow("Original CSV / Raw Phone", contact.rawPhone)
    DetailFieldRow("Import Status", contact.importStatus.label)
    DetailFieldRow("Email", contact.email ?: "—")
    DetailFieldRow("College / Institution", contact.college ?: "—")
    DetailFieldRow("City", contact.city ?: "—")
    DetailFieldRow("State", contact.state ?: "—")
    DetailFieldRow("Consent Status", contact.consentStatus.name)
    DetailFieldRow(
      "Import Timestamp",
      SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(contact.importedAt)),
    )

    if (contact.customFields.isNotEmpty()) {
      Spacer(modifier = Modifier.height(4.dp))
      Text("Custom CSV Fields", style = MaterialTheme.typography.titleSmall)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        contact.customFields.forEach { (key, value) ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "$key: $value",
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DetailFieldRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
  }
}

@Composable
private fun SuppressionListPane(
  suppressions: List<SuppressionRecord>,
  onAddSuppression: () -> Unit,
  onRemoveSuppression: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Recipients on this list are blocked from all campaign queues.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Button(onClick = onAddSuppression, modifier = Modifier.testTag("add_suppression_button")) {
        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Suppress Number")
      }
    }

    if (suppressions.isEmpty()) {
      EmptyStateCard(
        icon = Icons.Outlined.CheckCircle,
        title = "Suppression list is empty",
        description = "Opted-out numbers will appear here and will never receive campaign messages.",
      )
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suppressions, key = { it.phoneE164 }) { record ->
          Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(record.phoneE164, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${record.reason} • source: ${record.source}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              TextButton(onClick = { onRemoveSuppression(record.phoneE164) }) {
                Text("Remove")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddOrEditContactDialog(
  initial: ContactWithFields?,
  defaultCode: String,
  onDismiss: () -> Unit,
  onSave:
    (
      name: String,
      phone: String,
      email: String?,
      college: String?,
      city: String?,
      state: String?,
      consent: ConsentStatus,
      optedOut: Boolean,
      customFields: Map<String, String>,
    ) -> Unit,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var phone by remember { mutableStateOf(initial?.phoneE164 ?: "") }
  var email by remember { mutableStateOf(initial?.email ?: "") }
  var college by remember { mutableStateOf(initial?.college ?: "") }
  var city by remember { mutableStateOf(initial?.city ?: "") }
  var state by remember { mutableStateOf(initial?.state ?: "") }
  var optedOut by remember { mutableStateOf(initial?.isOptedOut ?: false) }
  var newKey by remember { mutableStateOf("") }
  var newVal by remember { mutableStateOf("") }
  val customEntries = remember {
    mutableStateListOf<Pair<String, String>>().apply {
      initial?.customFields?.forEach { (k, v) -> add(k to v) }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Add Contact" else "Edit Contact") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Full Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("contact_name_input"),
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Phone Number ($defaultCode fallback if toggled ON) *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("contact_phone_input"),
        )
        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = college,
          onValueChange = { college = it },
          label = { Text("College") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = state,
            onValueChange = { state = it },
            label = { Text("State") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Opted out / suppressed", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = optedOut, onCheckedChange = { optedOut = it })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Custom Fields", style = MaterialTheme.typography.titleSmall)
        customEntries.forEachIndexed { index, (k, v) ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("$k = $v", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { customEntries.removeAt(index) }) {
              Icon(Icons.Outlined.Close, contentDescription = "Remove custom field")
            }
          }
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newKey,
            onValueChange = { newKey = it },
            label = { Text("Key") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = newVal,
            onValueChange = { newVal = it },
            label = { Text("Value") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = {
              if (newKey.isNotBlank() && newVal.isNotBlank()) {
                customEntries.add(newKey.trim() to newVal.trim())
                newKey = ""
                newVal = ""
              }
            }
          ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add custom field")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            name,
            phone,
            email,
            college,
            city,
            state,
            if (optedOut) ConsentStatus.REVOKED else (initial?.consentStatus ?: ConsentStatus.GRANTED),
            optedOut,
            customEntries.toMap(),
          )
        },
        modifier = Modifier.testTag("save_contact_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun AddSuppressionDialog(
  defaultCode: String,
  onDismiss: () -> Unit,
  onSave: (phone: String, reason: String) -> Unit,
) {
  var phone by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("Recipient requested opt-out") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add to Suppression List") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Phone Number (e.g., $defaultCode 9876543210)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("suppression_phone_input"),
        )
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("Opt-Out Reason") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(phone, reason) },
        modifier = Modifier.testTag("confirm_add_suppression_button"),
      ) {
        Text("Suppress")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
