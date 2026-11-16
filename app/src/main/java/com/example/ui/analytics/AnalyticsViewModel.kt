package com.example.ui.analytics

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.repository.CampaignRepository
import com.example.data.repository.MessageLogRepository
import com.example.domain.model.CampaignStatsSummary
import com.example.domain.model.MessageStatus
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalyticsUiState(
  val campaigns: List<CampaignStatsSummary> = emptyList(),
  val selectedCampaignId: Long? = null,
  val selectedSummary: CampaignStatsSummary? = null,
  val selectedRecords: List<MessageRecordEntity> = emptyList(),
  val errorCodeBreakdown: Map<String, Int> = emptyMap(),
  val feedbackMessage: String? = null,
)

class AnalyticsViewModel(
  private val campaignRepository: CampaignRepository,
  messageLogRepository: MessageLogRepository,
) : ViewModel() {

  private val selectedIdFlow = MutableStateFlow<Long?>(null)
  private val feedbackFlow = MutableStateFlow<String?>(null)

  val uiState: StateFlow<AnalyticsUiState> =
    combine(
      campaignRepository.campaignSummaries,
      messageLogRepository.allMessages,
      selectedIdFlow,
      feedbackFlow,
    ) { summaries, allMessages, selectedId, feedback ->
      val resolvedId = selectedId ?: summaries.firstOrNull()?.campaign?.id
      val summary = summaries.firstOrNull { it.campaign.id == resolvedId } ?: summaries.firstOrNull()
      val records =
        if (summary != null) allMessages.filter { it.campaignId == summary.campaign.id }
        else emptyList()
      val errorBreakdown =
        records
          .filter { it.currentStatus == MessageStatus.FAILED || !it.errorCode.isNullOrBlank() }
          .groupBy { it.errorCode ?: "UNKNOWN" }
          .mapValues { it.value.size }

      AnalyticsUiState(
        campaigns = summaries,
        selectedCampaignId = summary?.campaign?.id,
        selectedSummary = summary,
        selectedRecords = records,
        errorCodeBreakdown = errorBreakdown,
        feedbackMessage = feedback,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = AnalyticsUiState(),
    )

  fun selectCampaign(campaignId: Long) {
    selectedIdFlow.value = campaignId
  }

  fun exportSelectedCampaignReport(contentResolver: ContentResolver, uri: Uri) {
    viewModelScope.launch {
      val summary = uiState.value.selectedSummary
      if (summary == null) {
        feedbackFlow.value = "Select a campaign to export"
        return@launch
      }
      val records = uiState.value.selectedRecords
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            val csv = campaignRepository.buildCampaignReportCsv(summary, records)
            contentResolver.openOutputStream(uri)?.use { out ->
              out.write(csv.toByteArray(StandardCharsets.UTF_8))
              out.flush()
            } ?: error("Unable to open export stream")
          }
        }
      feedbackFlow.value =
        result.fold(
          onSuccess = { "Exported campaign analytics CSV (${records.size} records)" },
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
      AnalyticsViewModel(campaignRepository, messageLogRepository) as T
  }
}
