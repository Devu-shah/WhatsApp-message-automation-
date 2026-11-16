package com.example.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CampaignStatsSummary
import com.example.ui.campaigns.CampaignStatusBadge
import com.example.ui.components.EmptyStateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampaignHistoryScreen(
  viewModel: CampaignHistoryViewModel,
  onNavigateCampaigns: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        viewModel.exportArchiveReport(context.contentResolver, uri)
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
    val isSplitPane = maxWidth >= 720.dp

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column {
        Text("Campaign History Archive", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Permanent local campaign ledger preserved across restarts. Safely clone configurations without auto-sending.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = viewModel::updateSearch,
        placeholder = { Text("Search campaigns by name, template, sender, or status") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.updateSearch("") }) {
              Icon(Icons.Outlined.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
      )

      if (uiState.entries.isEmpty()) {
        EmptyStateCard(
          icon = Icons.Outlined.History,
          title = "No archived campaigns yet",
          description = "Completed, paused, and draft campaigns appear here permanently for auditing, exporting, or cloning.",
          actionLabel = "Go to Campaigns",
          onAction = onNavigateCampaigns,
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
            items(uiState.entries, key = { it.campaign.id }) { entry ->
              HistoryRowCard(
                summary = entry,
                selected = uiState.selectedEntry?.campaign?.id == entry.campaign.id,
                onClick = { viewModel.selectCampaign(entry.campaign.id) },
              )
            }
          }
          Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
            uiState.selectedEntry?.let { selected ->
              HistoryDetailPane(
                summary = selected,
                onClone = { viewModel.cloneAsSafeDraft(selected.campaign.id, onNavigateCampaigns) },
                onExport = {
                  val safe = selected.campaign.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
                  exportLauncher.launch("reachhub_archive_$safe.csv")
                },
              )
            }
          }
        }
      } else {
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          uiState.entries.forEach { entry ->
            HistoryRowCard(
              summary = entry,
              selected = uiState.selectedEntry?.campaign?.id == entry.campaign.id,
              onClick = { viewModel.selectCampaign(entry.campaign.id) },
            )
          }
          uiState.selectedEntry?.let { selected ->
            HistoryDetailPane(
              summary = selected,
              onClone = { viewModel.cloneAsSafeDraft(selected.campaign.id, onNavigateCampaigns) },
              onExport = {
                val safe = selected.campaign.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
                exportLauncher.launch("reachhub_archive_$safe.csv")
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryRowCard(
  summary: CampaignStatsSummary,
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
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = summary.campaign.name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        CampaignStatusBadge(summary.campaign.status)
      }
      Text(
        text = "${summary.templateName} • ${summary.totalRecipients} recipients • ${summary.campaign.mode.name}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "Created ${formatHistoryDate(summary.campaign.createdAt)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun HistoryDetailPane(
  summary: CampaignStatsSummary,
  onClone: () -> Unit,
  onExport: () -> Unit,
) {
  val campaign = summary.campaign
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(campaign.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        CampaignStatusBadge(campaign.status)
      }
      if (campaign.description.isNotBlank()) {
        Text(campaign.description, style = MaterialTheme.typography.bodyMedium)
      }
      HorizontalDivider()
      HistorySpecRow("Mode", campaign.mode.name)
      HistorySpecRow("Template", summary.templateName)
      HistorySpecRow("Attached Flyer", summary.mediaName ?: "None")
      HistorySpecRow("Sender Number", campaign.senderPhoneSnapshot.ifBlank { "Default" })
      HistorySpecRow("Created", formatHistoryDate(campaign.createdAt))
      HistorySpecRow("Started", campaign.startedAt?.let { formatHistoryDate(it) } ?: "Not started")
      HistorySpecRow("Completed", campaign.completedAt?.let { formatHistoryDate(it) } ?: "—")
      HorizontalDivider()
      HistorySpecRow("Total Recipients", summary.totalRecipients.toString())
      HistorySpecRow("Delivered / Read", "${summary.deliveredCount + summary.readCount} (${summary.readCount} read)")
      HistorySpecRow("Failed / Skipped", "${summary.failedCount} failed • ${summary.skippedCount} skipped")
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onClone,
          modifier = Modifier.weight(1f).testTag("clone_campaign_button"),
        ) {
          Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Clone as Draft")
        }
        OutlinedButton(
          onClick = onExport,
          modifier = Modifier.weight(1f).testTag("export_history_report_button"),
        ) {
          Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Export Report")
        }
      }
    }
  }
}

@Composable
private fun HistorySpecRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
  }
}

private fun formatHistoryDate(ms: Long): String =
  SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
