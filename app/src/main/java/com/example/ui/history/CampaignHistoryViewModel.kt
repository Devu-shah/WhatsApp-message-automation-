package com.example.ui.history

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CampaignRepository
import com.example.data.repository.MessageLogRepository
import com.example.domain.model.CampaignStatsSummary
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CampaignHistoryUiState(
  val searchQuery: String = "",
  val entries: List<CampaignStatsSummary> = emptyList(),
  val selectedEntry: CampaignStatsSummary? = null,
  val feedbackMessage: String? = null,
)

class CampaignHistoryViewModel(
  private val campaignRepository: CampaignRepository,
  private val messageLogRepository: MessageLogRepository,
) : ViewModel() {

  private val searchQueryFlow = MutableStateFlow("")
  private val selectedIdFlow = MutableStateFlow<Long?>(null)
  private val feedbackFlow = MutableStateFlow<String?>(null)

  private val latestMessages = messageLogRepository.allMessages

  val uiState: StateFlow<CampaignHistoryUiState> =
    combine(
      campaignRepository.campaignSummaries,
      searchQueryFlow,
      selectedIdFlow,
      feedbackFlow,
    ) { allSummaries, query, selectedId, feedback ->
      val q = query.trim().lowercase()
      val filtered =
        if (q.isEmpty()) allSummaries
        else {
          allSummaries.filter { s ->
            s.campaign.name.lowercase().contains(q) ||
              s.templateName.lowercase().contains(q) ||
              s.campaign.senderPhoneSnapshot.lowercase().contains(q) ||
              s.campaign.status.name.lowercase().contains(q)
          }
        }
      val selected =
        filtered.firstOrNull { it.campaign.id == selectedId } ?: filtered.firstOrNull()
      CampaignHistoryUiState(
        searchQuery = query,
        entries = filtered,
        selectedEntry = selected,
        feedbackMessage = feedback,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = CampaignHistoryUiState(),
    )

  fun updateSearch(query: String) {
    searchQueryFlow.value = query
  }

  fun selectCampaign(campaignId: Long) {
    selectedIdFlow.value = campaignId
  }

  fun cloneAsSafeDraft(campaignId: Long, onClonedNavigateCampaigns: () -> Unit) {
    viewModelScope.launch {
      campaignRepository
        .cloneCampaignAsSafeDraft(campaignId)
        .onSuccess { newId ->
          selectedIdFlow.value = newId
          feedbackFlow.value = "Cloned campaign configuration as a new Draft (zero auto-send)"
          onClonedNavigateCampaigns()
        }
        .onFailure { err ->
          feedbackFlow.value = err.message ?: "Unable to clone campaign"
        }
    }
  }

  fun exportArchiveReport(contentResolver: ContentResolver, uri: Uri) {
    viewModelScope.launch {
      val selected = uiState.value.selectedEntry
      if (selected == null) {
        feedbackFlow.value = "Select an archived campaign to export"
        return@launch
      }
      val records =
        withContext(Dispatchers.IO) {
          latestMessages.first().filter { it.campaignId == selected.campaign.id }
        }
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            val csv = campaignRepository.buildCampaignReportCsv(selected, records)
            contentResolver.openOutputStream(uri)?.use { out ->
              out.write(csv.toByteArray(StandardCharsets.UTF_8))
              out.flush()
            } ?: error("Unable to open export file")
          }
        }
      feedbackFlow.value =
        result.fold(
          onSuccess = { "Exported historical report for '${selected.campaign.name}'" },
          onFailure = { "Export failed: ${it.message ?: "Unknown error"}" },
        )
    }
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(
    private val campaignRepository: CampaignRepository,
    private val messageLogRepository: MessageLogRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CampaignHistoryViewModel(campaignRepository, messageLogRepository) as T
  }
}
