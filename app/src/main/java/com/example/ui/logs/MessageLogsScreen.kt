package com.example.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
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
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.local.db.entity.MessageStatusEventEntity
import com.example.data.local.db.entity.RetryAttemptEntity
import com.example.domain.model.MessageLogFilter
import com.example.domain.model.MessageStatus
import com.example.domain.util.PhoneNumberHelper
import com.example.ui.components.EmptyStateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageLogsScreen(
  viewModel: MessageLogsViewModel,
  onNavigateCampaigns: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        viewModel.exportFilteredLogs(context.contentResolver, uri)
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Message Logs & Retries", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "${uiState.filteredRecords.size} message(s) • ${uiState.retryableCountInView} retryable failure(s)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val campaignFilter = uiState.selectedCampaignIdFilter
          if (campaignFilter != null && uiState.retryableCountInView > 0) {
            Button(
              onClick = { viewModel.bulkRetryCampaign(campaignFilter) },
              modifier = Modifier.testTag("logs_bulk_retry_button"),
            ) {
              Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Retry All (${uiState.retryableCountInView})")
            }
          }
          OutlinedButton(
            onClick = { exportLauncher.launch("reachhub_message_logs.csv") },
            modifier = Modifier.testTag("export_logs_csv_button"),
          ) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export CSV")
          }
        }
      }

      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = viewModel::updateSearch,
        placeholder = { Text("Search by recipient name, phone, WhatsApp ID, or error code") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.updateSearch("") }) {
              Icon(Icons.Outlined.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("logs_search_input"),
      )

      // Status Filters
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MessageLogFilter.entries.forEach { filter ->
          FilterChip(
            selected = uiState.activeFilter == filter,
            onClick = { viewModel.updateFilter(filter) },
            label = { Text(filter.label) },
          )
        }
      }

      // Campaign Scope Filters
      if (uiState.campaigns.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = uiState.selectedCampaignIdFilter == null,
            onClick = { viewModel.updateCampaignFilter(null) },
            label = { Text("All Campaigns") },
          )
          uiState.campaigns.forEach { campaign ->
            FilterChip(
              selected = uiState.selectedCampaignIdFilter == campaign.id,
              onClick = { viewModel.updateCampaignFilter(campaign.id) },
              label = { Text(campaign.name) },
            )
          }
        }
      }

      if (uiState.filteredRecords.isEmpty()) {
        EmptyStateCard(
          icon = Icons.Outlined.ReceiptLong,
          title = "No matching message records",
          description = "Adjust filters or create and run a campaign to inspect per-recipient lifecycle logs.",
          actionLabel = if (uiState.campaigns.isEmpty()) "Open Campaigns" else null,
          onAction = if (uiState.campaigns.isEmpty()) onNavigateCampaigns else null,
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
            items(uiState.filteredRecords, key = { it.id }) { msg ->
              MessageLogRowCard(
                record = msg,
                selected = uiState.selectedRecord?.id == msg.id,
                onClick = { viewModel.selectMessage(msg.id) },
              )
            }
          }
          Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
            uiState.selectedRecord?.let { selected ->
              MessageLogDetailPane(
                record = selected,
                events = uiState.selectedRecordEvents,
                retries = uiState.selectedRecordRetries,
                maxRetryAttempts = uiState.maxRetryAttempts,
                onRetrySingle = { viewModel.retrySingleMessage(selected.id) },
              )
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(uiState.filteredRecords, key = { it.id }) { msg ->
            MessageLogRowCard(
              record = msg,
              selected = uiState.selectedRecord?.id == msg.id,
              onClick = { viewModel.selectMessage(msg.id) },
            )
          }
          uiState.selectedRecord?.let { selected ->
            item {
              MessageLogDetailPane(
                record = selected,
                events = uiState.selectedRecordEvents,
                retries = uiState.selectedRecordRetries,
                maxRetryAttempts = uiState.maxRetryAttempts,
                onRetrySingle = { viewModel.retrySingleMessage(selected.id) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MessageLogRowCard(
  record: MessageRecordEntity,
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
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = record.recipientName,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${PhoneNumberHelper.maskForDisplay(record.recipientPhoneE164)} • Attempts: ${record.attemptCount}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!record.errorCode.isNullOrBlank()) {
          Text(
            text =
              "${record.errorCode} (${if (record.isPermanentError) "Permanent" else "Retryable"})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      MessageStatusChip(record.currentStatus)
    }
  }
}

@Composable
private fun MessageLogDetailPane(
  record: MessageRecordEntity,
  events: List<MessageStatusEventEntity>,
  retries: List<RetryAttemptEntity>,
  maxRetryAttempts: Int,
  onRetrySingle: () -> Unit,
) {
  val canRetry =
    record.currentStatus == MessageStatus.FAILED &&
      !record.isPermanentError &&
      record.attemptCount < maxRetryAttempts

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
        Column(modifier = Modifier.weight(1f)) {
          Text(record.recipientName, style = MaterialTheme.typography.titleLarge)
          Text(
            text = record.recipientPhoneE164,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        MessageStatusChip(record.currentStatus)
      }

      if (record.currentStatus == MessageStatus.FAILED) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text =
                if (record.isPermanentError) "Permanent Error (${record.errorCode ?: "N/A"}) — Retry Blocked"
                else "Temporary Error (${record.errorCode ?: "N/A"}) — Attempts ${record.attemptCount}/$maxRetryAttempts",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!record.errorDescription.isNullOrBlank()) {
              Text(
                text = record.errorDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
      }

      if (canRetry) {
        Button(
          onClick = onRetrySingle,
          modifier = Modifier.testTag("retry_single_message_button"),
        ) {
          Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Retry Message Now")
        }
      }

      Text("Personalized Message Body", style = MaterialTheme.typography.titleSmall)
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = record.personalizedBody,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(12.dp),
        )
      }

      Text(
        text = "WhatsApp Message ID: ${record.whatsappMessageId ?: "Pending"}\nIdempotency Key: ${record.idempotencyKey}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider()
      Text("Status Audit Timeline (${events.size})", style = MaterialTheme.typography.titleSmall)
      events.forEach { ev ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("${ev.status.name} • ${ev.source}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (!ev.detail.isNullOrBlank()) {
              Text(ev.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Text(formatLogTime(ev.eventTimestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      if (retries.isNotEmpty()) {
        HorizontalDivider()
        Text("Retry Attempts (${retries.size})", style = MaterialTheme.typography.titleSmall)
        retries.forEach { retry ->
          Text(
            text = "Attempt #${retry.attemptNumber} • ${retry.outcome.name} • ${retry.errorDetail ?: ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun MessageStatusChip(status: MessageStatus) {
  val (bg, fg) =
    when (status) {
      MessageStatus.READ, MessageStatus.DELIVERED ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
      MessageStatus.SUBMITTED, MessageStatus.SENT ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      MessageStatus.FAILED ->
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
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

private fun formatLogTime(ms: Long): String =
  SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(ms))
