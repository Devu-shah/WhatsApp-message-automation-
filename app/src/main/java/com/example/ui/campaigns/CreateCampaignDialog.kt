package com.example.ui.campaigns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudienceSelectionMode
import com.example.domain.model.MediaAsset
import com.example.domain.model.MessageTemplate
import com.example.domain.model.PreDispatchAudit
import com.example.domain.model.SenderProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCampaignDialog(
  uiState: CampaignsUiState,
  computeAudit: (MessageTemplate?, AudienceSelectionMode, String, Set<Long>?) -> PreDispatchAudit,
  onDismiss: () -> Unit,
  onConfirmCreate: (
    name: String,
    description: String,
    template: MessageTemplate,
    media: MediaAsset?,
    sender: SenderProfile,
    audit: PreDispatchAudit,
    launchNow: Boolean,
  ) -> Unit,
) {
  var step by remember { mutableIntStateOf(0) } // 0 = Setup, 1 = Audience, 2 = Pre-Dispatch Audit
  var campaignName by remember { mutableStateOf("") }
  var campaignDescription by remember { mutableStateOf("") }
  var selectedTemplate by remember { mutableStateOf(uiState.templates.firstOrNull()) }
  var selectedMedia by remember { mutableStateOf<MediaAsset?>(null) }
  var selectedSender by remember {
    mutableStateOf(
      uiState.senderProfiles.firstOrNull { it.isDefault } ?: uiState.senderProfiles.firstOrNull()
    )
  }
  var audienceMode by remember { mutableStateOf(AudienceSelectionMode.ALL_ELIGIBLE) }
  var audienceFilterQuery by remember { mutableStateOf("") }
  var auditConfirmed by remember { mutableStateOf(false) }

  var templateExpanded by remember { mutableStateOf(false) }
  var mediaExpanded by remember { mutableStateOf(false) }
  var senderExpanded by remember { mutableStateOf(false) }

  val audit =
    remember(
      selectedTemplate,
      audienceMode,
      audienceFilterQuery,
      uiState.contacts,
      uiState.suppressedPhones,
    ) {
      computeAudit(selectedTemplate, audienceMode, audienceFilterQuery, null)
    }

  val missingSetupPrerequisites =
    uiState.templates.isEmpty() || uiState.senderProfiles.isEmpty() || uiState.contacts.isEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          when (step) {
            0 -> "Create Campaign (Step 1/3: Setup)"
            1 -> "Select Audience (Step 2/3)"
            else -> "Pre-Dispatch Audit & Confirm (Step 3/3)"
          }
      )
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (missingSetupPrerequisites) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                buildString {
                  append("Missing prerequisites before launching a campaign:\n")
                  if (uiState.senderProfiles.isEmpty()) append("• Add a WhatsApp Business sender number in Settings.\n")
                  if (uiState.templates.isEmpty()) append("• Create at least one template in Templates.\n")
                  if (uiState.contacts.isEmpty()) append("• Import or add contacts first.")
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else {
          when (step) {
            0 -> {
              OutlinedTextField(
                value = campaignName,
                onValueChange = { campaignName = it },
                label = { Text("Campaign Name *") },
                placeholder = { Text("e.g., Monsoon Campus Drive Wave 1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("campaign_name_input"),
              )
              OutlinedTextField(
                value = campaignDescription,
                onValueChange = { campaignDescription = it },
                label = { Text("Description / Notes") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
              )

              // Sender Dropdown
              ExposedDropdownMenuBox(
                expanded = senderExpanded,
                onExpandedChange = { senderExpanded = it },
              ) {
                OutlinedTextField(
                  value =
                    selectedSender?.let { "${it.label} (${it.phoneNumberE164})" }
                      ?: "Select Sender Line",
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("WhatsApp Business Sender *") },
                  trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = senderExpanded)
                  },
                  modifier =
                    Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth()
                      .testTag("campaign_sender_dropdown"),
                )
                ExposedDropdownMenu(
                  expanded = senderExpanded,
                  onDismissRequest = { senderExpanded = false },
                ) {
                  uiState.senderProfiles.forEach { sender ->
                    DropdownMenuItem(
                      text = { Text("${sender.label} (${sender.phoneNumberE164})") },
                      onClick = {
                        selectedSender = sender
                        senderExpanded = false
                      },
                    )
                  }
                }
              }

              // Template Dropdown
              ExposedDropdownMenuBox(
                expanded = templateExpanded,
                onExpandedChange = { templateExpanded = it },
              ) {
                OutlinedTextField(
                  value =
                    selectedTemplate?.let { "${it.name} [${it.validationStatus.name}]" }
                      ?: "Select Template",
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Message Template *") },
                  trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateExpanded)
                  },
                  modifier =
                    Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth()
                      .testTag("campaign_template_dropdown"),
                )
                ExposedDropdownMenu(
                  expanded = templateExpanded,
                  onDismissRequest = { templateExpanded = false },
                ) {
                  uiState.templates.forEach { template ->
                    DropdownMenuItem(
                      text = { Text("${template.name} (${template.metaTemplateName})") },
                      onClick = {
                        selectedTemplate = template
                        templateExpanded = false
                      },
                    )
                  }
                }
              }

              // Flyer Media Dropdown
              ExposedDropdownMenuBox(
                expanded = mediaExpanded,
                onExpandedChange = { mediaExpanded = it },
              ) {
                OutlinedTextField(
                  value = selectedMedia?.displayName ?: "No Flyer Attached (Text Only)",
                  onValueChange = {},
                  readOnly = true,
                  label = {
                    Text(
                      if (selectedTemplate?.headerMediaRequired == true) "Flyer Image * (Required by Template)"
                      else "Optional Flyer Image"
                    )
                  },
                  trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = mediaExpanded)
                  },
                  modifier =
                    Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth()
                      .testTag("campaign_media_dropdown"),
                )
                ExposedDropdownMenu(
                  expanded = mediaExpanded,
                  onDismissRequest = { mediaExpanded = false },
                ) {
                  DropdownMenuItem(
                    text = { Text("None (Text-only template)") },
                    onClick = {
                      selectedMedia = null
                      mediaExpanded = false
                    },
                  )
                  uiState.mediaAssets.forEach { asset ->
                    DropdownMenuItem(
                      text = { Text("${asset.displayName} (${asset.mimeType})") },
                      onClick = {
                        selectedMedia = asset
                        mediaExpanded = false
                      },
                    )
                  }
                }
              }
            }

            1 -> {
              Text(
                text = "Choose how recipients are filtered before suppression & validation checks:",
                style = MaterialTheme.typography.bodySmall,
              )
              AudienceSelectionMode.entries.forEach { mode ->
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  RadioButton(
                    selected = audienceMode == mode,
                    onClick = { audienceMode = mode },
                  )
                  Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                }
              }
              if (audienceMode == AudienceSelectionMode.FILTER_BY_QUERY) {
                OutlinedTextField(
                  value = audienceFilterQuery,
                  onValueChange = { audienceFilterQuery = it },
                  label = { Text("Filter by City, College, Name, or Custom Field") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text =
                    "Matched ${audit.totalEvaluated} contacts • ${audit.validEligibleCount} valid & eligible (${audit.optedOutSuppressedCount} suppressed, ${audit.invalidPhoneCount} invalid)",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            2 -> {
              Surface(
                color =
                  if (uiState.settings.simulationModeEnabled)
                    MaterialTheme.colorScheme.tertiaryContainer
                  else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text =
                    if (uiState.settings.simulationModeEnabled)
                      "SIMULATION SANDBOX: Messages will run locally with zero Meta API charges."
                    else
                      "PRODUCTION DISPATCH: Submitting will send live WhatsApp messages from ${selectedSender?.phoneNumberE164}.",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color =
                    if (uiState.settings.simulationModeEnabled)
                      MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }

              Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text("Pre-Dispatch Audit Breakdown", style = MaterialTheme.typography.titleSmall)
                  AuditStatRow("Contacts Evaluated", audit.totalEvaluated.toString())
                  AuditStatRow("Valid Eligible Recipients", audit.validEligibleCount.toString())
                  AuditStatRow("Opted-Out / Suppressed Blocked", audit.optedOutSuppressedCount.toString())
                  AuditStatRow("Duplicate Phones Collapsed", audit.duplicatePhoneCount.toString())
                  AuditStatRow("Invalid E.164 Phones Excluded", audit.invalidPhoneCount.toString())
                  AuditStatRow("Missing Placeholder Values", audit.missingPlaceholderCount.toString())
                }
              }

              Text("Sample Personalized Preview", style = MaterialTheme.typography.titleSmall)
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = audit.sampleRenderedMessage,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(12.dp),
                )
              }

              HorizontalDivider()

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Checkbox(
                  checked = auditConfirmed,
                  onCheckedChange = { auditConfirmed = it },
                  modifier = Modifier.testTag("confirm_dispatch_audit_checkbox"),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "I verified recipient eligibility, template copy, and attached flyer.",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (missingSetupPrerequisites) {
        Button(onClick = onDismiss) { Text("Close") }
      } else if (step < 2) {
        Button(
          onClick = { step++ },
          enabled =
            when (step) {
              0 ->
                campaignName.isNotBlank() &&
                  selectedTemplate != null &&
                  selectedSender != null &&
                  (selectedTemplate?.headerMediaRequired != true || selectedMedia != null)
              1 -> audit.validEligibleCount > 0
              else -> true
            },
          modifier = Modifier.testTag("campaign_wizard_next_button"),
        ) {
          Text("Next")
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            enabled = auditConfirmed && selectedTemplate != null && selectedSender != null && audit.validEligibleCount > 0,
            onClick = {
              onConfirmCreate(
                campaignName,
                campaignDescription,
                selectedTemplate!!,
                selectedMedia,
                selectedSender!!,
                audit,
                false,
              )
            },
            modifier = Modifier.testTag("save_campaign_draft_button"),
          ) {
            Text("Save Ready")
          }
          Button(
            enabled = auditConfirmed && selectedTemplate != null && selectedSender != null && audit.validEligibleCount > 0,
            onClick = {
              onConfirmCreate(
                campaignName,
                campaignDescription,
                selectedTemplate!!,
                selectedMedia,
                selectedSender!!,
                audit,
                true,
              )
            },
            modifier = Modifier.testTag("launch_campaign_now_button"),
          ) {
            Text("Launch")
          }
        }
      }
    },
    dismissButton = {
      Row {
        if (step > 0 && !missingSetupPrerequisites) {
          TextButton(onClick = { step-- }) { Text("Back") }
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

@Composable
private fun AuditStatRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
  }
}
