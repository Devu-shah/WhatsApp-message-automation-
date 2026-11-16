package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DashboardRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.DashboardStats
import com.example.domain.model.OperatorSettings
import com.example.domain.model.RecentCampaignSummary
import com.example.domain.model.RecentMessageActivity
import com.example.domain.model.SenderProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface DashboardUiState {
  data object Loading : DashboardUiState

  data class Success(
    val stats: DashboardStats,
    val recentCampaigns: List<RecentCampaignSummary>,
    val recentActivity: List<RecentMessageActivity>,
    val settings: OperatorSettings,
    val activeSenderProfile: SenderProfile?,
  ) : DashboardUiState
}

class DashboardViewModel(
  dashboardRepository: DashboardRepository,
  settingsRepository: SettingsRepository,
) : ViewModel() {

  val uiState: StateFlow<DashboardUiState> =
    combine(
        dashboardRepository.dashboardStats,
        dashboardRepository.recentCampaigns,
        dashboardRepository.recentMessageActivity,
        settingsRepository.settings,
        settingsRepository.defaultSenderProfile,
      ) { stats, campaigns, activity, settings, activeSender ->
        DashboardUiState.Success(
          stats = stats,
          recentCampaigns = campaigns,
          recentActivity = activity,
          settings = settings,
          activeSenderProfile = activeSender,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading,
      )

  class Factory(
    private val dashboardRepository: DashboardRepository,
    private val settingsRepository: SettingsRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      DashboardViewModel(dashboardRepository, settingsRepository) as T
  }
}
