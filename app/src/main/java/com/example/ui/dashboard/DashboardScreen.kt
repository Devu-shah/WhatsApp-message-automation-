package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.RecentCampaignSummary
import com.example.domain.model.RecentMessageActivity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricKpiCard
import com.example.ui.components.SimulationModeBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  onNavigateCsvImport: () -> Unit,
  onNavigateCampaigns: () -> Unit,
  onNavigateTemplates: () -> Unit,
  onNavigateLogs: () -> Unit,
  onNavigateSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  when (val state = uiState) {
    DashboardUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    is DashboardUiState.Success -> {
      BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideTablet = maxWidth >= 840.dp
        val isMedium = maxWidth >= 600.dp

        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = if (isMedium) 24.dp else 16.dp, vertical = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            modifier = Modifier.widthIn(max = 1180.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
          ) {
            SimulationModeBanner(
              isSimulation = state.settings.simulationModeEnabled,
              activeSenderPhone = state.activeSenderProfile?.phoneNumberE164,
              defaultCountryCode = state.settings.defaultCountryCode,
              autoApplyDefaultCode = state.settings.autoApplyDefaultCountryCode,
              onOpenSettings = onNavigateSettings,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
              )
              Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            // Quick Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(
                text = stringResource(R.string.quick_actions_title),
                style = MaterialTheme.typography.titleMedium,
              )
              if (isMedium) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  QuickActionButton(
                    label = stringResource(R.string.action_import_contacts),
                    icon = Icons.Outlined.UploadFile,
                    isPrimary = true,
                    testTag = "quick_action_import_contacts",
                    onClick = onNavigateCsvImport,
                    modifier = Modifier.weight(1f),
                  )
                  QuickActionButton(
                    label = stringResource(R.string.action_create_campaign),
                    icon = Icons.Outlined.Campaign,
                    isPrimary = false,
                    testTag = "quick_action_create_campaign",
                    onClick = onNavigateCampaigns,
                    modifier = Modifier.weight(1f),
                  )
                  QuickActionButton(
                    label = stringResource(R.string.action_manage_templates),
                    icon = Icons.Outlined.Description,
                    isPrimary = false,
                    testTag = "quick_action_manage_templates",
                    onClick = onNavigateTemplates,
                    modifier = Modifier.weight(1f),
                  )
                  QuickActionButton(
                    label = stringResource(R.string.action_view_logs),
                    icon = Icons.Outlined.ReceiptLong,
                    isPrimary = false,
                    testTag = "quick_action_view_logs",
                    onClick = onNavigateLogs,
                    modifier = Modifier.weight(1f),
                  )
                }
              } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton(
                      label = stringResource(R.string.action_import_contacts),
                      icon = Icons.Outlined.UploadFile,
                      isPrimary = true,
                      testTag = "quick_action_import_contacts",
                      onClick = onNavigateCsvImport,
                      modifier = Modifier.weight(1f),
                    )
                    QuickActionButton(
                      label = stringResource(R.string.action_create_campaign),
                      icon = Icons.Outlined.Campaign,
                      isPrimary = false,
                      testTag = "quick_action_create_campaign",
                      onClick = onNavigateCampaigns,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton(
                      label = stringResource(R.string.action_manage_templates),
                      icon = Icons.Outlined.Description,
                      isPrimary = false,
                      testTag = "quick_action_manage_templates",
                      onClick = onNavigateTemplates,
                      modifier = Modifier.weight(1f),
                    )
                    QuickActionButton(
                      label = stringResource(R.string.action_view_logs),
                      icon = Icons.Outlined.ReceiptLong,
                      isPrimary = false,
                      testTag = "quick_action_view_logs",
                      onClick = onNavigateLogs,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
            }

            // 8 Real KPI Metrics Grid (4 columns on wide tablet, 2 columns on compact)
            val kpis =
              listOf(
                KpiItem(
                  title = "Total Contacts",
                  value = state.stats.totalContacts.toString(),
                  subtitle = "${state.stats.optedOutContacts} opted out suppressed",
                  icon = Icons.Outlined.Groups,
                  accent = true,
                ),
                KpiItem(
                  title = "Total Campaigns",
                  value = state.stats.totalCampaigns.toString(),
                  subtitle = "${state.stats.totalTemplates} saved templates",
                  icon = Icons.Outlined.Campaign,
                ),
                KpiItem(
                  title = "Active Campaigns",
                  value = state.stats.activeCampaigns.toString(),
                  subtitle = "Ready, running, or paused",
                  icon = Icons.Outlined.PlayCircleOutline,
                ),
                KpiItem(
                  title = "Completed Campaigns",
                  value = state.stats.completedCampaigns.toString(),
                  subtitle = "${state.stats.totalMediaAssets} media flyers saved",
                  icon = Icons.Outlined.TaskAlt,
                ),
                KpiItem(
                  title = "Messages Submitted",
                  value = state.stats.totalMessagesSubmitted.toString(),
                  subtitle = "Accepted by dispatcher",
                  icon = Icons.AutoMirrored.Outlined.Send,
                ),
                KpiItem(
                  title = "Messages Delivered",
                  value = state.stats.messagesDelivered.toString(),
                  subtitle = "Confirmed delivery receipts",
                  icon = Icons.Outlined.DoneAll,
                ),
                KpiItem(
                  title = "Messages Read",
                  value = state.stats.messagesRead.toString(),
                  subtitle = "Where read receipts enabled",
                  icon = Icons.Outlined.MarkChatRead,
                ),
                KpiItem(
                  title = "Failed Messages",
                  value = state.stats.failedMessages.toString(),
                  subtitle = "Eligible & permanent errors",
                  icon = Icons.Outlined.ErrorOutline,
                ),
              )

            val columns = if (isWideTablet) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              kpis.chunked(columns).forEach { rowItems ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  rowItems.forEach { item ->
                    MetricKpiCard(
                      title = item.title,
                      value = item.value,
                      subtitle = item.subtitle,
                      icon = item.icon,
                      accentContainer = item.accent,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
              }
            }

            // Recent Campaigns & Recent Message Activity
            if (isWideTablet) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
              ) {
                RecentCampaignsPanel(
                  campaigns = state.recentCampaigns,
                  onNavigateCampaigns = onNavigateCampaigns,
                  modifier = Modifier.weight(1f),
                )
                RecentActivityPanel(
                  activity = state.recentActivity,
                  onNavigateLogs = onNavigateLogs,
                  modifier = Modifier.weight(1f),
                )
              }
            } else {
              RecentCampaignsPanel(
                campaigns = state.recentCampaigns,
                onNavigateCampaigns = onNavigateCampaigns,
              )
              RecentActivityPanel(
                activity = state.recentActivity,
                onNavigateLogs = onNavigateLogs,
              )
            }
          }
        }
      }
    }
  }
}

private data class KpiItem(
  val title: String,
  val value: String,
  val subtitle: String,
  val icon: ImageVector,
  val accent: Boolean = false,
)

@Composable
private fun QuickActionButton(
  label: String,
  icon: ImageVector,
  isPrimary: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (isPrimary) {
    Button(
      onClick = onClick,
      modifier = modifier.height(52.dp).testTag(testTag),
      shape = MaterialTheme.shapes.medium,
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.size(8.dp))
      Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  } else {
    FilledTonalButton(
      onClick = onClick,
      modifier = modifier.height(52.dp).testTag(testTag),
      shape = MaterialTheme.shapes.medium,
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.size(8.dp))
      Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun RecentCampaignsPanel(
  campaigns: List<RecentCampaignSummary>,
  onNavigateCampaigns: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = stringResource(R.string.recent_campaigns_title),
      style = MaterialTheme.typography.titleMedium,
    )
    if (campaigns.isEmpty()) {
      EmptyStateCard(
        icon = Icons.Outlined.Campaign,
        title = stringResource(R.string.empty_campaigns_title),
        description = stringResource(R.string.empty_campaigns_body),
        actionLabel = "Campaigns Roadmap",
        onAction = onNavigateCampaigns,
      )
    } else {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          campaigns.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${item.mode.name} • ${formatTimestamp(item.createdAt)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = item.status.name,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RecentActivityPanel(
  activity: List<RecentMessageActivity>,
  onNavigateLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = stringResource(R.string.recent_activity_title),
      style = MaterialTheme.typography.titleMedium,
    )
    if (activity.isEmpty()) {
      EmptyStateCard(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.empty_activity_title),
        description = stringResource(R.string.empty_activity_body),
        actionLabel = "View Logs Module",
        onAction = onNavigateLogs,
      )
    } else {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          activity.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.recipientName, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${item.recipientPhoneMasked} • ${formatTimestamp(item.updatedAt)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = item.status.name,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun formatTimestamp(epochMillis: Long): String =
  SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(epochMillis))
