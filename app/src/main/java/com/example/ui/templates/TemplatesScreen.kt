package com.example.ui.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ContactWithFields
import com.example.domain.model.MessageTemplate
import com.example.domain.model.TemplateParameterMapping
import com.example.domain.model.TemplateValidationStatus
import com.example.domain.util.TemplateEngine
import com.example.ui.components.EmptyStateCard

@Composable
fun TemplatesScreen(
  viewModel: TemplatesViewModel,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var searchQuery by remember { mutableStateOf("") }
  var isCreatingNew by remember { mutableStateOf(false) }
  var templatePendingDelete by remember { mutableStateOf<MessageTemplate?>(null) }

  LaunchedEffect(uiState.feedbackMessage) {
    val msg = uiState.feedbackMessage
    if (msg != null) {
      onShowMessage(msg)
      viewModel.clearFeedback()
    }
  }

  val filteredTemplates =
    remember(uiState.templates, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) uiState.templates
      else
        uiState.templates.filter {
          it.name.lowercase().contains(q) ||
            it.metaTemplateName.lowercase().contains(q) ||
            it.bodyText.lowercase().contains(q)
        }
    }

  val activeTemplate =
    if (isCreatingNew) null
    else uiState.templates.firstOrNull { it.id == uiState.selectedTemplateId }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isSplitPane = maxWidth >= 720.dp

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Message Template Manager", style = MaterialTheme.typography.headlineMedium)
          Text(
            text =
              "Map readable {Placeholders} to Meta {{1}} positional parameters and preview per contact.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = {
            isCreatingNew = true
            viewModel.selectTemplate(null)
          },
          modifier = Modifier.testTag("new_template_button"),
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Template")
        }
      }

      if (isSplitPane) {
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Left: Saved Templates List
          Column(
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search templates...") },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("templates_search_input"),
            )
            if (filteredTemplates.isEmpty()) {
              EmptyStateCard(
                icon = Icons.Outlined.Description,
                title = "No saved templates yet",
                description =
                  "Create a reusable template in the editor pane with placeholders like {Name}, {College}, and {City}.",
              )
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(filteredTemplates, key = { it.id }) { tpl ->
                  TemplateListCard(
                    template = tpl,
                    selected = !isCreatingNew && activeTemplate?.id == tpl.id,
                    onClick = {
                      isCreatingNew = false
                      viewModel.selectTemplate(tpl.id)
                    },
                    onDuplicate = {
                      isCreatingNew = false
                      viewModel.duplicateTemplate(tpl)
                    },
                    onDelete = { templatePendingDelete = tpl },
                  )
                }
              }
            }
          }

          // Right: Template Editor + Validator + Live Preview
          TemplateEditorPane(
            template = activeTemplate,
            isNew = isCreatingNew || activeTemplate == null,
            availableFields = uiState.availableContactFields,
            contactsForPreview = uiState.contactsForPreview,
            viewModel = viewModel,
            onSaved = { isCreatingNew = false },
            modifier = Modifier.weight(1.4f).fillMaxHeight(),
          )
        }
      } else {
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (filteredTemplates.isNotEmpty()) {
            filteredTemplates.forEach { tpl ->
              TemplateListCard(
                template = tpl,
                selected = !isCreatingNew && activeTemplate?.id == tpl.id,
                onClick = {
                  isCreatingNew = false
                  viewModel.selectTemplate(tpl.id)
                },
                onDuplicate = {
                  isCreatingNew = false
                  viewModel.duplicateTemplate(tpl)
                },
                onDelete = { templatePendingDelete = tpl },
              )
            }
          }
          TemplateEditorPane(
            template = activeTemplate,
            isNew = isCreatingNew || activeTemplate == null,
            availableFields = uiState.availableContactFields,
            contactsForPreview = uiState.contactsForPreview,
            viewModel = viewModel,
            onSaved = { isCreatingNew = false },
          )
        }
      }
    }
  }

  templatePendingDelete?.let { tpl ->
    AlertDialog(
      onDismissRequest = { templatePendingDelete = null },
      title = { Text("Delete Template?") },
      text = { Text("Delete '${tpl.name}' (${tpl.metaTemplateName}) from ReachHub?") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            viewModel.deleteTemplate(tpl.id)
            templatePendingDelete = null
          },
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { templatePendingDelete = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun TemplateListCard(
  template: MessageTemplate,
  selected: Boolean,
  onClick: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
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
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = template.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${template.metaTemplateName} • ${template.languageCode} • ${template.category}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row {
          IconButton(onClick = onDuplicate) {
            Icon(
              Icons.Outlined.ContentCopy,
              contentDescription = "Duplicate ${template.name}",
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Outlined.DeleteOutline,
              contentDescription = "Delete ${template.name}",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
      Text(
        text = template.bodyText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val statusColor =
          when (template.validationStatus) {
            TemplateValidationStatus.VALID -> MaterialTheme.colorScheme.tertiaryContainer
            TemplateValidationStatus.NEEDS_MAPPING -> MaterialTheme.colorScheme.secondaryContainer
            TemplateValidationStatus.INVALID -> MaterialTheme.colorScheme.errorContainer
          }
        Surface(color = statusColor, shape = RoundedCornerShape(6.dp)) {
          Text(
            text = template.validationStatus.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
        if (template.headerMediaRequired) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = "IMAGE HEADER",
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorPane(
  template: MessageTemplate?,
  isNew: Boolean,
  availableFields: List<String>,
  contactsForPreview: List<ContactWithFields>,
  viewModel: TemplatesViewModel,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val editKey = if (isNew) -1L else template?.id ?: -1L
  var displayName by remember(editKey) { mutableStateOf(template?.name ?: "") }
  var metaName by remember(editKey) { mutableStateOf(template?.metaTemplateName ?: "") }
  var languageCode by remember(editKey) { mutableStateOf(template?.languageCode ?: "en") }
  var category by remember(editKey) { mutableStateOf(template?.category ?: "MARKETING") }
  var headerMedia by remember(editKey) { mutableStateOf(template?.headerMediaRequired ?: false) }
  var bodyText by
    remember(editKey) {
      mutableStateOf(
        template?.bodyText
          ?: "Hello {Name},\nWe are pleased to invite you to our upcoming event at {College}, {City}.\nWe look forward to your participation!"
      )
    }
  var mappings by
    remember(editKey) {
      mutableStateOf(
        TemplateEngine.syncMappings(
          bodyText = template?.bodyText
            ?: "Hello {Name},\nWe are pleased to invite you to our upcoming event at {College}, {City}.\nWe look forward to your participation!",
          existingMappings = template?.mappings ?: emptyList(),
          availableFields = availableFields,
        )
      )
    }

  var previewContactIdx by remember { mutableIntStateOf(0) }
  var contactMenuExpanded by remember { mutableStateOf(false) }

  fun updateBody(newBody: String) {
    bodyText = newBody
    mappings = TemplateEngine.syncMappings(newBody, mappings, availableFields)
  }

  val report =
    remember(displayName, metaName, bodyText, mappings, headerMedia, availableFields) {
      viewModel.validateDraft(displayName, metaName, bodyText, mappings, headerMedia)
    }
  val contactsMissingCount =
    remember(bodyText, mappings, contactsForPreview) {
      viewModel.countContactsWithMissingValues(bodyText, mappings)
    }

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (isNew) "New Message Template" else "Edit Template",
          style = MaterialTheme.typography.titleLarge,
        )
        Button(
          onClick = {
            viewModel.saveTemplate(
              id = if (isNew) 0L else template?.id ?: 0L,
              name = displayName,
              metaTemplateName = metaName,
              languageCode = languageCode,
              category = category,
              bodyText = bodyText,
              mappings = mappings,
              headerMediaRequired = headerMedia,
            )
            onSaved()
          },
          modifier = Modifier.testTag("save_template_button"),
        ) {
          Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Save Template")
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = displayName,
          onValueChange = {
            displayName = it
            if (metaName.isEmpty() || isNew) {
              metaName =
                it.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            }
          },
          label = { Text("Template Display Name *") },
          placeholder = { Text("Campus Event Invite") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("template_display_name_input"),
        )
        OutlinedTextField(
          value = metaName,
          onValueChange = { metaName = it.lowercase() },
          label = { Text("Meta Approved Template Name *") },
          placeholder = { Text("campus_event_invite") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("template_meta_name_input"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = languageCode,
          onValueChange = { languageCode = it },
          label = { Text("Language Code") },
          singleLine = true,
          modifier = Modifier.weight(0.6f),
        )
        OutlinedTextField(
          value = category,
          onValueChange = { category = it.uppercase() },
          label = { Text("Category") },
          singleLine = true,
          modifier = Modifier.weight(0.8f),
        )
        Row(
          modifier = Modifier.weight(1.2f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.End,
        ) {
          Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text("Image Header", style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.width(8.dp))
          Switch(checked = headerMedia, onCheckedChange = { headerMedia = it })
        }
      }

      // Insert Placeholder Chips (Standard + CSV Custom Columns)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Insert Dynamic Placeholder:", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          availableFields.forEach { field ->
            AssistChip(
              onClick = { updateBody("$bodyText {$field}") },
              label = { Text("{$field}") },
            )
          }
        }
      }

      OutlinedTextField(
        value = bodyText,
        onValueChange = ::updateBody,
        label = { Text("Message Body ({Placeholder} syntax) *") },
        minLines = 5,
        maxLines = 9,
        supportingText = {
          Text("${bodyText.length} / ${TemplateEngine.MAX_META_BODY_CHARS} chars")
        },
        modifier = Modifier.fillMaxWidth().testTag("template_body_input"),
      )

      // Positional Parameter Mapping Table
      if (mappings.isNotEmpty()) {
        HorizontalDivider()
        Text(
          text = "Meta Positional Parameter Mapping ({{1}}, {{2}}, ...)",
          style = MaterialTheme.typography.titleSmall,
        )
        mappings.forEachIndexed { idx, mapping ->
          var fieldDropdownOpen by remember(mapping.placeholder) { mutableStateOf(false) }
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = "{${mapping.placeholder}} → {{${mapping.metaIndex}}}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              )
            }
            ExposedDropdownMenuBox(
              expanded = fieldDropdownOpen,
              onExpandedChange = { fieldDropdownOpen = !fieldDropdownOpen },
              modifier = Modifier.weight(1f),
            ) {
              OutlinedTextField(
                value = mapping.contactFieldKey,
                onValueChange = {},
                readOnly = true,
                label = { Text("Contact / CSV Column") },
                trailingIcon = {
                  ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownOpen)
                },
                singleLine = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = fieldDropdownOpen,
                onDismissRequest = { fieldDropdownOpen = false },
              ) {
                availableFields.forEach { field ->
                  DropdownMenuItem(
                    text = { Text(field) },
                    onClick = {
                      val updated = mappings.toMutableList()
                      updated[idx] = mapping.copy(contactFieldKey = field)
                      mappings = updated
                      fieldDropdownOpen = false
                    },
                  )
                }
              }
            }
            OutlinedTextField(
              value = mapping.fallbackValue,
              onValueChange = { fallback ->
                val updated = mappings.toMutableList()
                updated[idx] = mapping.copy(fallbackValue = fallback)
                mappings = updated
              },
              label = { Text("Fallback") },
              singleLine = true,
              modifier = Modifier.weight(0.8f),
            )
          }
        }
      }

      // Meta Compatibility & Compiled Preview
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Meta Cloud API Compiled Payload", style = MaterialTheme.typography.titleSmall)
          Text(
            text = report.metaCompiledBody,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          report.errors.forEach { err ->
            Text("Error: $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
          report.warnings.forEach { warn ->
            Text("Note: $warn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      // Personalized Contact Preview
      HorizontalDivider()
      Text("Personalized Contact Preview", style = MaterialTheme.typography.titleSmall)
      if (contactsForPreview.isEmpty()) {
        Text(
          text = "Import or add contacts in Contacts / CSV Import to preview live personalization.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val safeIdx = previewContactIdx.coerceIn(0, contactsForPreview.lastIndex)
        val sampleContact = contactsForPreview[safeIdx]
        val rendered = viewModel.renderPreview(bodyText, mappings, sampleContact)

        ExposedDropdownMenuBox(
          expanded = contactMenuExpanded,
          onExpandedChange = { contactMenuExpanded = !contactMenuExpanded },
        ) {
          OutlinedTextField(
            value = "${sampleContact.name} (${sampleContact.phoneE164})",
            onValueChange = {},
            readOnly = true,
            label = { Text("Preview Contact") },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = contactMenuExpanded)
            },
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = contactMenuExpanded,
            onDismissRequest = { contactMenuExpanded = false },
          ) {
            contactsForPreview.take(40).forEachIndexed { idx, c ->
              DropdownMenuItem(
                text = { Text("${c.name} • ${c.city ?: c.phoneE164}") },
                onClick = {
                  previewContactIdx = idx
                  contactMenuExpanded = false
                },
              )
            }
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(rendered.renderedText, style = MaterialTheme.typography.bodyMedium)
            if (rendered.missingPlaceholders.isNotEmpty()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Outlined.WarningAmber,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text =
                    "Missing values for this contact: ${rendered.missingPlaceholders.joinToString { "{$it}" }}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
            if (contactsMissingCount > 0) {
              Text(
                text =
                  "$contactsMissingCount of ${contactsForPreview.size} eligible contacts have at least one empty placeholder value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
