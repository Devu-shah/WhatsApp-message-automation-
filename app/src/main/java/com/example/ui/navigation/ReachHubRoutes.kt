package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface ReachHubRoute {
  @Serializable data object Dashboard : ReachHubRoute

  @Serializable data object Contacts : ReachHubRoute

  @Serializable data object CsvImport : ReachHubRoute

  @Serializable data object Templates : ReachHubRoute

  @Serializable data object MediaLibrary : ReachHubRoute

  @Serializable data object Campaigns : ReachHubRoute

  @Serializable data object Analytics : ReachHubRoute

  @Serializable data object MessageLogs : ReachHubRoute

  @Serializable data object CampaignHistory : ReachHubRoute

  @Serializable data object Settings : ReachHubRoute
}

data class ReachHubDestination(
  val route: ReachHubRoute,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
  val showInCompactBar: Boolean = false,
  val phaseBadge: String? = null,
)

val reachHubDestinations =
  listOf(
    ReachHubDestination(
      route = ReachHubRoute.Dashboard,
      label = "Dashboard",
      selectedIcon = Icons.Filled.Dashboard,
      unselectedIcon = Icons.Outlined.Dashboard,
      testTag = "nav_dashboard",
      showInCompactBar = true,
    ),
    ReachHubDestination(
      route = ReachHubRoute.Contacts,
      label = "Contacts",
      selectedIcon = Icons.Filled.Contacts,
      unselectedIcon = Icons.Outlined.Contacts,
      testTag = "nav_contacts",
      showInCompactBar = true,
    ),
    ReachHubDestination(
      route = ReachHubRoute.CsvImport,
      label = "CSV Import",
      selectedIcon = Icons.Filled.UploadFile,
      unselectedIcon = Icons.Outlined.UploadFile,
      testTag = "nav_csv_import",
    ),
    ReachHubDestination(
      route = ReachHubRoute.Templates,
      label = "Templates",
      selectedIcon = Icons.Filled.Description,
      unselectedIcon = Icons.Outlined.Description,
      testTag = "nav_templates",
    ),
    ReachHubDestination(
      route = ReachHubRoute.MediaLibrary,
      label = "Media",
      selectedIcon = Icons.Filled.PermMedia,
      unselectedIcon = Icons.Outlined.PermMedia,
      testTag = "nav_media",
    ),
    ReachHubDestination(
      route = ReachHubRoute.Campaigns,
      label = "Campaigns",
      selectedIcon = Icons.Filled.Campaign,
      unselectedIcon = Icons.Outlined.Campaign,
      testTag = "nav_campaigns",
      showInCompactBar = true,
    ),
    ReachHubDestination(
      route = ReachHubRoute.Analytics,
      label = "Analytics",
      selectedIcon = Icons.Filled.Analytics,
      unselectedIcon = Icons.Outlined.Analytics,
      testTag = "nav_analytics",
    ),
    ReachHubDestination(
      route = ReachHubRoute.MessageLogs,
      label = "Logs",
      selectedIcon = Icons.Filled.ReceiptLong,
      unselectedIcon = Icons.Outlined.ReceiptLong,
      testTag = "nav_logs",
    ),
    ReachHubDestination(
      route = ReachHubRoute.CampaignHistory,
      label = "History",
      selectedIcon = Icons.Filled.History,
      unselectedIcon = Icons.Outlined.History,
      testTag = "nav_history",
    ),
    ReachHubDestination(
      route = ReachHubRoute.Settings,
      label = "Settings",
      selectedIcon = Icons.Filled.Settings,
      unselectedIcon = Icons.Outlined.Settings,
      testTag = "nav_settings",
      showInCompactBar = true,
    ),
  )
