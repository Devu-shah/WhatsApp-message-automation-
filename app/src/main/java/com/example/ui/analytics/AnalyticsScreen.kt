package com.example.ui.analytics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
  viewModel: AnalyticsViewModel,
  onNavigateCampaigns: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var dropdownExpanded by remember { mutableStateOf(false) }

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        viewModel.exportSelectedCampaignReport(context.contentResolver, uri)
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
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Campaign Analytics", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Real delivery funnels, timings, and failure analysis computed directly from logged events.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (uiState.selectedSummary != null) {
        Button(
          onClick = {
            val safeName =
              uiState.selectedSummary!!.campaign.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
            exportLauncher.launch("reachhub_report_$safeName.csv")
          },
          modifier = Modifier.testTag("export_analytics_report_button"),
        ) {
          Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Export Report")
        }
      }
    }

    val selected = uiState.selectedSummary
    if (selected == null) {
      EmptyStateCard(
        icon = Icons.Outlined.Analytics,
        title = "No campaign telemetry yet",
        description = "Launch a campaign in Simulation or Production mode to inspect delivery rates and funnel breakdowns.",
        actionLabel = "Open Campaign Manager",
        onAction = onNavigateCampaigns,
      )
    } else {
      ExposedDropdownMenuBox(
        expanded = dropdownExpanded,
        onExpandedChange = { dropdownExpanded = it },
      ) {
        OutlinedTextField(
          value = "${selected.campaign.name} (${selected.campaign.mode.name} • ${selected.campaign.status.name})",
          onValueChange = {},
          readOnly = true,
          label = { Text("Inspecting Campaign") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
          modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = dropdownExpanded,
          onDismissRequest = { dropdownExpanded = false },
        ) {
          uiState.campaigns.forEach { item ->
            DropdownMenuItem(
              text = { Text("${item.campaign.name} (${item.totalRecipients} recipients)") },
              onClick = {
                viewModel.selectCampaign(item.campaign.id)
                dropdownExpanded = false
              },
            )
          }
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AnalyticsKpiCard(
          label = "Total Recipients",
          value = selected.totalRecipients.toString(),
          caption = "Sender: ${selected.campaign.senderPhoneSnapshot}",
          modifier = Modifier.weight(1f, fill = false).width(210.dp),
        )
        AnalyticsKpiCard(
          label = "Delivery Rate",
          value = String.format(Locale.US, "%.1f%%", selected.deliveryRatePercent),
          caption = "${selected.deliveredCount + selected.readCount} delivered or read",
          modifier = Modifier.weight(1f, fill = false).width(210.dp),
        )
        AnalyticsKpiCard(
          label = "Read Rate",
          value = String.format(Locale.US, "%.1f%%", selected.readRatePercent),
          caption = "${selected.readCount} opened receipts",
          modifier = Modifier.weight(1f, fill = false).width(210.dp),
        )
        AnalyticsKpiCard(
          label = "Failure Rate",
          value = String.format(Locale.US, "%.1f%%", selected.failureRatePercent),
          caption = "${selected.retryableFailedCount} retryable • ${selected.permanentFailedCount} permanent",
          modifier = Modifier.weight(1f, fill = false).width(210.dp),
        )
      }

      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 720.dp
        val funnelCard: @Composable (Modifier) -> Unit = { mod ->
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = mod,
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text("Delivery Funnel Distribution", style = MaterialTheme.typography.titleLarge)
              val maxVal = selected.totalRecipients.coerceAtLeast(1)
              val bars =
                listOf(
                  FunnelBarData("Queued", selected.queuedCount, MaterialTheme.colorScheme.secondary),
                  FunnelBarData("Submitted", selected.submittedCount, MaterialTheme.colorScheme.primary),
                  FunnelBarData("Sent", selected.sentCount, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                  FunnelBarData("Delivered", selected.deliveredCount, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)),
                  FunnelBarData("Read", selected.readCount, MaterialTheme.colorScheme.tertiary),
                  FunnelBarData("Failed", selected.failedCount, MaterialTheme.colorScheme.error),
                  FunnelBarData("Skipped", selected.skippedCount, MaterialTheme.colorScheme.outline),
                )
              bars.forEach { bar ->
                FunnelBarRow(bar = bar, maxCount = maxVal)
              }
            }
          }
        }

        val timingAndErrorsCard: @Composable (Modifier) -> Unit = { mod ->
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = mod,
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("Timing & Error Diagnostics", style = MaterialTheme.typography.titleLarge)
              TimingRow("Mode", selected.campaign.mode.name)
              TimingRow("Started", formatTimestamp(selected.campaign.startedAt))
              TimingRow("Completed", formatTimestamp(selected.campaign.completedAt))
              TimingRow(
                "Duration",
                formatDuration(selected.campaign.startedAt, selected.campaign.completedAt),
              )
              HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
              Text("Error Code Breakdown", style = MaterialTheme.typography.titleSmall)
              if (uiState.errorCodeBreakdown.isEmpty()) {
                Text(
                  text = "No message failures recorded for this campaign.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                uiState.errorCodeBreakdown.forEach { (code, count) ->
                  TimingRow("Code $code", "$count message(s)")
                }
              }
            }
          }
        }

        if (wide) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            funnelCard(Modifier.weight(1.2f))
            timingAndErrorsCard(Modifier.weight(0.9f))
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            funnelCard(Modifier.fillMaxWidth())
            timingAndErrorsCard(Modifier.fillMaxWidth())
          }
        }
      }
    }
  }
}

private data class FunnelBarData(
  val label: String,
  val count: Int,
  val color: Color,
)

@Composable
private fun FunnelBarRow(bar: FunnelBarData, maxCount: Int) {
  val ratio = (bar.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(bar.label, style = MaterialTheme.typography.bodyMedium)
      Text("${bar.count}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
      drawRoundRect(
        color = trackColor,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(8f, 8f),
      )
      if (ratio > 0f) {
        drawRoundRect(
          color = bar.color,
          size = Size((size.width * ratio).coerceAtLeast(8f), size.height),
          cornerRadius = CornerRadius(8f, 8f),
        )
      }
    }
  }
}

@Composable
private fun AnalyticsKpiCard(
  label: String,
  value: String,
  caption: String,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun TimingRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.labelMedium)
  }
}

private fun formatTimestamp(ms: Long?): String {
  if (ms == null) return "Pending"
  return SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(ms))
}

private fun formatDuration(start: Long?, end: Long?): String {
  if (start == null || end == null || end < start) return "—"
  val secs = (end - start) / 1000L
  return if (secs < 60) "${secs}s" else "${secs / 60}m ${secs % 60}s"
}
