package com.example.ui.campaigns

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AppMode
import com.example.domain.model.CampaignStatsSummary
import com.example.domain.model.CampaignStatus
import com.example.domain.util.PhoneNumberHelper
import com.example.ui.components.EmptyStateCard

@Composable
fun CampaignsScreen(
  viewModel: CampaignsViewModel,
  onNavigateLogs: () -> Unit,
  onNavigateAnalytics: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showCreateDialog by remember { mutableStateOf(false) }
  var campaignPendingDelete by remember { mutableStateOf<CampaignStatsSummary?>(null) }

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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Campaign Manager", style = MaterialTheme.typography.headlineMedium)
          Text(
            text =
              if (uiState.settings.simulationModeEnabled)
                "Active Engine: Simulation Sandbox (Safe local testing • Batch ${uiState.settings.batchSize})"
              else
                "Active Engine: Google Cloud Run Production (Meta Cloud API • Batch ${uiState.settings.batchSize})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = { showCreateDialog = true },
          modifier = Modifier.testTag("create_campaign_fab"),
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Campaign")
        }
      }

      if (uiState.campaigns.isEmpty()) {
        EmptyStateCard(
          icon = Icons.Outlined.Campaign,
          title = "No campaigns created yet",
          description =
            "Combine a validated template, verified contacts, optional JPEG/PNG flyer, and your WhatsApp Business sender line to run a Simulation or Production campaign.",
          actionLabel = "Create First Campaign",
          onAction = { showCreateDialog = true },
        )
      } else if (isSplitPane) {
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          LazyColumn(
            modifier = Modifier.weight(0.95f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(uiState.campaigns, key = { it.campaign.id }) { summary ->
              CampaignListCard(
                summary = summary,
                selected = uiState.selectedSummary?.campaign?.id == summary.campaign.id,
                onClick = { viewModel.selectCampaign(summary.campaign.id) },
              )
            }
          }
          Box(modifier = Modifier.weight(1.25f).fillMaxHeight()) {
            uiState.selectedSummary?.let { selected ->
              CampaignControlPane(
                summary = selected,
                recentMessages = uiState.selectedCampaignMessages.take(25),
                onLaunchDraft = { viewModel.launchDraft(selected.campaign.id) },
                onPause = { viewModel.pauseCampaign(selected.campaign.id) },
                onResume = { viewModel.resumeCampaign(selected.campaign.id) },
                onCancel = { viewModel.cancelCampaign(selected.campaign.id) },
                onRetryFailed = { viewModel.retryEligibleFailed(selected.campaign.id) },
                onSyncWebhooks = { viewModel.syncWebhooksFromCloudRun(selected.campaign.id) },
                onViewLogs = onNavigateLogs,
                onViewAnalytics = onNavigateAnalytics,
                onDelete = { campaignPendingDelete = selected },
              )
            }
          }
        }
      } else {
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          uiState.campaigns.forEach { summary ->
            CampaignListCard(
              summary = summary,
              selected = uiState.selectedSummary?.campaign?.id == summary.campaign.id,
              onClick = { viewModel.selectCampaign(summary.campaign.id) },
            )
          }
          uiState.selectedSummary?.let { selected ->
            CampaignControlPane(
              summary = selected,
              recentMessages = uiState.selectedCampaignMessages.take(15),
              onLaunchDraft = { viewModel.launchDraft(selected.campaign.id) },
              onPause = { viewModel.pauseCampaign(selected.campaign.id) },
              onResume = { viewModel.resumeCampaign(selected.campaign.id) },
              onCancel = { viewModel.cancelCampaign(selected.campaign.id) },
              onRetryFailed = { viewModel.retryEligibleFailed(selected.campaign.id) },
              onSyncWebhooks = { viewModel.syncWebhooksFromCloudRun(selected.campaign.id) },
              onViewLogs = onNavigateLogs,
              onViewAnalytics = onNavigateAnalytics,
              onDelete = { campaignPendingDelete = selected },
            )
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateCampaignDialog(
      uiState = uiState,
      computeAudit = viewModel::computeAudit,
      onDismiss = { showCreateDialog = false },
      onConfirmCreate = { name, desc, template, media, sender, audit, launchNow ->
        viewModel.createAndLaunchCampaign(name, desc, template, media, sender, audit, launchNow)
        showCreateDialog = false
      },
    )
  }

  campaignPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { campaignPendingDelete = null },
      title = { Text("Delete Campaign?") },
      text = {
        Text(
          "Delete '${target.campaign.name}' and all its local message delivery logs? This cannot be undone."
        )
      },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            viewModel.deleteCampaign(target.campaign.id)
            campaignPendingDelete = null
          },
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { campaignPendingDelete = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun CampaignListCard(
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
      verticalArrangement = Arrangement.spacedBy(8.dp),
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
        Spacer(Modifier.width(8.dp))
        CampaignStatusBadge(summary.campaign.status)
      }
      Text(
        text =
          "${summary.campaign.mode.name} • ${summary.templateName} • ${summary.totalRecipients} recipients",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      LinearProgressIndicator(
        progress = { summary.progressFraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Delivered: ${summary.deliveredCount + summary.readCount}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
          text = "Read: ${summary.readCount}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Failed: ${summary.failedCount}",
          style = MaterialTheme.typography.labelSmall,
          color =
            if (summary.failedCount > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampaignControlPane(
  summary: CampaignStatsSummary,
  recentMessages: List<com.example.data.local.db.entity.MessageRecordEntity>,
  onLaunchDraft: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onCancel: () -> Unit,
  onRetryFailed: () -> Unit,
  onSyncWebhooks: () -> Unit,
  onViewLogs: () -> Unit,
  onViewAnalytics: () -> Unit,
  onDelete: () -> Unit,
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(campaign.name, style = MaterialTheme.typography.titleLarge)
          Text(
            text =
              "Sender: ${campaign.senderPhoneSnapshot.ifBlank { "Default" }} • Template: ${summary.templateName}" +
                (summary.mediaName?.let { " • Flyer: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        CampaignStatusBadge(campaign.status)
      }

      LinearProgressIndicator(
        progress = { summary.progressFraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatChip("Total", summary.totalRecipients)
        StatChip("Queued", summary.queuedCount)
        StatChip("Submitted", summary.submittedCount)
        StatChip("Sent", summary.sentCount)
        StatChip("Delivered", summary.deliveredCount)
        StatChip("Read", summary.readCount)
        StatChip("Failed", summary.failedCount)
        StatChip("Skipped", summary.skippedCount)
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (campaign.status == CampaignStatus.DRAFT || campaign.status == CampaignStatus.READY) {
          Button(
            onClick = onLaunchDraft,
            modifier = Modifier.testTag("campaign_launch_draft_button"),
          ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Start Campaign")
          }
        }
        if (campaign.status == CampaignStatus.RUNNING) {
          OutlinedButton(
            onClick = onPause,
            modifier = Modifier.testTag("campaign_pause_button"),
          ) {
            Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pause")
          }
        }
        if (campaign.status == CampaignStatus.PAUSED) {
          Button(
            onClick = onResume,
            modifier = Modifier.testTag("campaign_resume_button"),
          ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Resume")
          }
        }
        if (campaign.status == CampaignStatus.RUNNING || campaign.status == CampaignStatus.PAUSED) {
          OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.testTag("campaign_cancel_button"),
          ) {
            Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cancel")
          }
        }
        if (summary.retryableFailedCount > 0) {
          Button(
            onClick = onRetryFailed,
            modifier = Modifier.testTag("campaign_retry_failed_button"),
          ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry Eligible (${summary.retryableFailedCount})")
          }
        }
        if (campaign.mode == AppMode.PRODUCTION) {
          OutlinedButton(onClick = onSyncWebhooks) {
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sync Webhooks")
          }
        }
        OutlinedButton(onClick = onViewAnalytics) { Text("Analytics") }
        OutlinedButton(onClick = onViewLogs) { Text("Logs") }
        if (campaign.status != CampaignStatus.RUNNING) {
          TextButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Delete")
          }
        }
      }

      HorizontalDivider()
      Text("Recipient Queue Snapshot (${recentMessages.size} shown)", style = MaterialTheme.typography.titleSmall)
      if (recentMessages.isEmpty()) {
        Text(
          text = "No dispatched message records yet. Start this draft to populate recipient records.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        recentMessages.forEach { msg ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(msg.recipientName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
              Text(
                text = "${PhoneNumberHelper.maskForDisplay(msg.recipientPhoneE164)} • Attempts: ${msg.attemptCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = msg.currentStatus.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatChip(label: String, value: Int) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(8.dp),
  ) {
    Text(
      text = "$label: $value",
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}

@Composable
fun CampaignStatusBadge(status: CampaignStatus) {
  val (bg, fg) =
    when (status) {
      CampaignStatus.COMPLETED ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
      CampaignStatus.RUNNING ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      CampaignStatus.FAILED, CampaignStatus.CANCELLED ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
      else ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
  Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
    Text(
      text = status.name,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
