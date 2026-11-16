package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.AppContainer
import com.example.R
import com.example.ui.analytics.AnalyticsScreen
import com.example.ui.analytics.AnalyticsViewModel
import com.example.ui.campaigns.CampaignsScreen
import com.example.ui.campaigns.CampaignsViewModel
import com.example.ui.contacts.ContactsScreen
import com.example.ui.contacts.ContactsViewModel
import com.example.ui.csvimport.CsvImportScreen
import com.example.ui.csvimport.CsvImportViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.history.CampaignHistoryScreen
import com.example.ui.history.CampaignHistoryViewModel
import com.example.ui.logs.MessageLogsScreen
import com.example.ui.logs.MessageLogsViewModel
import com.example.ui.media.MediaLibraryScreen
import com.example.ui.media.MediaLibraryViewModel
import com.example.ui.navigation.ReachHubRoute
import com.example.ui.navigation.reachHubDestinations
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.templates.TemplatesScreen
import com.example.ui.templates.TemplatesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReachHubApp(appContainer: AppContainer) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  fun navigateTo(route: ReachHubRoute) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val showMessage: (String) -> Unit = { msg ->
    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    if (useNavigationRail) {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          ReachHubNavigationRail(
            currentRouteSelected = { route ->
              currentDestination?.hasRoute(route::class) == true
            },
            onSelectDestination = ::navigateTo,
          )
          Box(
            modifier =
              Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
          ) {
            ReachHubNavHost(
              navController = navController,
              appContainer = appContainer,
              onNavigate = ::navigateTo,
              onShowMessage = showMessage,
            )
          }
        }
      }
    } else {
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet {
            Column(
              modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              )
              HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
              reachHubDestinations.forEach { dest ->
                val selected = currentDestination?.hasRoute(dest.route::class) == true
                NavigationDrawerItem(
                  label = { Text(dest.label) },
                  selected = selected,
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = dest.label,
                    )
                  },
                  badge =
                    dest.phaseBadge?.let { badge ->
                      {
                        Text(
                          text = badge,
                          style = MaterialTheme.typography.labelSmall,
                        )
                      }
                    },
                  onClick = {
                    coroutineScope.launch { drawerState.close() }
                    navigateTo(dest.route)
                  },
                )
              }
            }
          }
        },
      ) {
        Scaffold(
          topBar = {
            TopAppBar(
              title = {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.titleLarge,
                )
              },
              navigationIcon = {
                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                  Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Open navigation menu",
                  )
                }
              },
            )
          },
          bottomBar = {
            NavigationBar {
              reachHubDestinations
                .filter { it.showInCompactBar }
                .forEach { dest ->
                  val selected = currentDestination?.hasRoute(dest.route::class) == true
                  NavigationBarItem(
                    selected = selected,
                    onClick = { navigateTo(dest.route) },
                    icon = {
                      Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label,
                      )
                    },
                    label = {
                      Text(
                        text = dest.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    },
                    modifier = Modifier.testTag(dest.testTag),
                  )
                }
            }
          },
          snackbarHost = { SnackbarHost(snackbarHostState) },
          contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
          Box(
            modifier =
              Modifier.fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
          ) {
            ReachHubNavHost(
              navController = navController,
              appContainer = appContainer,
              onNavigate = ::navigateTo,
              onShowMessage = showMessage,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReachHubNavigationRail(
  currentRouteSelected: (ReachHubRoute) -> Boolean,
  onSelectDestination: (ReachHubRoute) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    modifier = Modifier.fillMaxHeight(),
  ) {
    NavigationRail(
      modifier =
        Modifier.fillMaxHeight()
          .width(96.dp)
          .verticalScroll(rememberScrollState())
          .windowInsetsPadding(WindowInsets(0))
          .padding(vertical = 12.dp),
      header = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(bottom = 12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "RH",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "ReachHub",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
      },
    ) {
      reachHubDestinations.forEach { dest ->
        val selected = currentRouteSelected(dest.route)
        NavigationRailItem(
          selected = selected,
          onClick = { onSelectDestination(dest.route) },
          icon = {
            Icon(
              imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
              contentDescription = dest.label,
            )
          },
          label = {
            Text(
              text = dest.label,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          modifier = Modifier.testTag(dest.testTag),
        )
      }
    }
  }
}

@Composable
private fun ReachHubNavHost(
  navController: NavHostController,
  appContainer: AppContainer,
  onNavigate: (ReachHubRoute) -> Unit,
  onShowMessage: (String) -> Unit,
) {
  NavHost(navController = navController, startDestination = ReachHubRoute.Dashboard) {
    composable<ReachHubRoute.Dashboard> {
      val dashboardViewModel: DashboardViewModel =
        viewModel(
          factory =
            DashboardViewModel.Factory(
              dashboardRepository = appContainer.dashboardRepository,
              settingsRepository = appContainer.settingsRepository,
            )
        )
      DashboardScreen(
        viewModel = dashboardViewModel,
        onNavigateCsvImport = { onNavigate(ReachHubRoute.CsvImport) },
        onNavigateCampaigns = { onNavigate(ReachHubRoute.Campaigns) },
        onNavigateTemplates = { onNavigate(ReachHubRoute.Templates) },
        onNavigateLogs = { onNavigate(ReachHubRoute.MessageLogs) },
        onNavigateSettings = { onNavigate(ReachHubRoute.Settings) },
      )
    }

    composable<ReachHubRoute.Contacts> {
      val contactsViewModel: ContactsViewModel =
        viewModel(
          factory =
            ContactsViewModel.Factory(
              contactRepository = appContainer.contactRepository,
              settingsRepository = appContainer.settingsRepository,
            )
        )
      ContactsScreen(
        viewModel = contactsViewModel,
        onNavigateCsvImport = { onNavigate(ReachHubRoute.CsvImport) },
        onShowMessage = onShowMessage,
      )
    }

    composable<ReachHubRoute.CsvImport> {
      val csvImportViewModel: CsvImportViewModel =
        viewModel(
          factory =
            CsvImportViewModel.Factory(
              contactRepository = appContainer.contactRepository,
              settingsRepository = appContainer.settingsRepository,
            )
        )
      CsvImportScreen(
        viewModel = csvImportViewModel,
        onNavigateContacts = { onNavigate(ReachHubRoute.Contacts) },
        onShowMessage = onShowMessage,
      )
    }

    composable<ReachHubRoute.Templates> {
      val templatesViewModel: TemplatesViewModel =
        viewModel(
          factory =
            TemplatesViewModel.Factory(
              templateRepository = appContainer.templateRepository,
              contactRepository = appContainer.contactRepository,
            )
        )
      TemplatesScreen(viewModel = templatesViewModel, onShowMessage = onShowMessage)
    }

    composable<ReachHubRoute.MediaLibrary> {
      val mediaLibraryViewModel: MediaLibraryViewModel =
        viewModel(factory = MediaLibraryViewModel.Factory(appContainer.mediaRepository))
      MediaLibraryScreen(viewModel = mediaLibraryViewModel, onShowMessage = onShowMessage)
    }

    composable<ReachHubRoute.Campaigns> {
      val campaignsViewModel: CampaignsViewModel =
        viewModel(
          factory =
            CampaignsViewModel.Factory(
              campaignRepository = appContainer.campaignRepository,
              contactRepository = appContainer.contactRepository,
              templateRepository = appContainer.templateRepository,
              mediaRepository = appContainer.mediaRepository,
              messageLogRepository = appContainer.messageLogRepository,
              settingsRepository = appContainer.settingsRepository,
            )
        )
      CampaignsScreen(
        viewModel = campaignsViewModel,
        onNavigateLogs = { onNavigate(ReachHubRoute.MessageLogs) },
        onNavigateAnalytics = { onNavigate(ReachHubRoute.Analytics) },
        onShowMessage = onShowMessage,
      )
    }

    composable<ReachHubRoute.Analytics> {
      val analyticsViewModel: AnalyticsViewModel =
        viewModel(
          factory =
            AnalyticsViewModel.Factory(
              campaignRepository = appContainer.campaignRepository,
              messageLogRepository = appContainer.messageLogRepository,
            )
        )
      AnalyticsScreen(
        viewModel = analyticsViewModel,
        onNavigateCampaigns = { onNavigate(ReachHubRoute.Campaigns) },
        onShowMessage = onShowMessage,
      )
    }

    composable<ReachHubRoute.MessageLogs> {
      val messageLogsViewModel: MessageLogsViewModel =
        viewModel(
          factory =
            MessageLogsViewModel.Factory(
              messageLogRepository = appContainer.messageLogRepository,
              campaignRepository = appContainer.campaignRepository,
              settingsRepository = appContainer.settingsRepository,
            )
        )
      MessageLogsScreen(
        viewModel = messageLogsViewModel,
        onNavigateCampaigns = { onNavigate(ReachHubRoute.Campaigns) },
        onShowMessage = onShowMessage,
      )
    }

    composable<ReachHubRoute.CampaignHistory> {
      val historyViewModel: CampaignHistoryViewModel =
        viewModel(
          factory =
            CampaignHistoryViewModel.Factory(
              campaignRepository = appContainer.campaignRepository,
              messageLogRepository = appContainer.messageLogRepository,
            )
        )
      CampaignHistoryScreen(
        viewModel = historyViewModel,
        onNavigateCampaigns = { onNavigate(ReachHubRoute.Campaigns) },
        onShowMessage = onShowMessage,
      )
    }

    composable<ReachHubRoute.Settings> {
      val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.Factory(appContainer.settingsRepository))
      SettingsScreen(viewModel = settingsViewModel, onShowMessage = onShowMessage)
    }
  }
}
