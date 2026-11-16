package com.example.ui.logs

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.entity.CampaignEntity
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.local.db.entity.MessageStatusEventEntity
import com.example.data.local.db.entity.RetryAttemptEntity
import com.example.data.repository.CampaignRepository
import com.example.data.repository.MessageLogRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.MessageLogFilter
import com.example.domain.model.MessageStatus
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MessageLogsUiState(
  val campaigns: List<CampaignEntity> = emptyList(),
  val selectedCampaignIdFilter: Long? = null,
  val searchQuery: String = "",
  val activeFilter: MessageLogFilter = MessageLogFilter.ALL,
  val filteredRecords: List<MessageRecordEntity> = emptyList(),
  val selectedRecord: MessageRecordEntity? = null,
  val selectedRecordEvents: List<MessageStatusEventEntity> = emptyList(),
  val selectedRecordRetries: List<RetryAttemptEntity> = emptyList(),
  val retryableCountInView: Int = 0,
  val maxRetryAttempts: Int = 3,
  val feedbackMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MessageLogsViewModel(
  private val messageLogRepository: MessageLogRepository,
  private val campaignRepository: CampaignRepository,
  settingsRepository: SettingsRepository,
) : ViewModel() {

  private val searchQueryFlow = MutableStateFlow("")
  private val statusFilterFlow = MutableStateFlow(MessageLogFilter.ALL)
  private val campaignFilterFlow = MutableStateFlow<Long?>(null)
  private val selectedMessageIdFlow = MutableStateFlow<Long?>(null)
  private val feedbackFlow = MutableStateFlow<String?>(null)

  private val timelineFlow =
    selectedMessageIdFlow.flatMapLatest { id ->
      if (id == null) flowOf(emptyList<MessageStatusEventEntity>() to emptyList<RetryAttemptEntity>())
      else {
        combine(
          messageLogRepository.observeEventsForMessage(id),
          messageLogRepository.observeRetryAttemptsForMessage(id),
        ) { events, retries ->
          events to retries
        }
      }
    }

  val uiState: StateFlow<MessageLogsUiState> =
    combine(
      combine(
        messageLogRepository.allMessages,
        campaignRepository.allCampaigns,
        settingsRepository.settings,
      ) { messages, campaigns, settings ->
        Triple(messages, campaigns, settings.maxRetryAttempts)
      },
      combine(
        searchQueryFlow,
        statusFilterFlow,
        campaignFilterFlow,
        selectedMessageIdFlow,
      ) { query, filter, campaignId, selectedMsgId ->
        FilterParams(query, filter, campaignId, selectedMsgId)
      },
      timelineFlow,
      feedbackFlow,
    ) { (allMessages, campaigns, maxRetries), params, (events, retries), feedback ->
      val q = params.query.trim().lowercase()
      val filtered =
        allMessages.filter { msg ->
          val matchesCampaign =
            params.campaignId == null || msg.campaignId == params.campaignId
          val matchesStatus =
            when (params.filter) {
              MessageLogFilter.ALL -> true
              MessageLogFilter.QUEUED -> msg.currentStatus == MessageStatus.QUEUED
              MessageLogFilter.SUBMITTED -> msg.currentStatus == MessageStatus.SUBMITTED
              MessageLogFilter.SENT -> msg.currentStatus == MessageStatus.SENT
              MessageLogFilter.DELIVERED -> msg.currentStatus == MessageStatus.DELIVERED
              MessageLogFilter.READ -> msg.currentStatus == MessageStatus.READ
              MessageLogFilter.FAILED -> msg.currentStatus == MessageStatus.FAILED
              MessageLogFilter.RETRYABLE_FAILED ->
                msg.currentStatus == MessageStatus.FAILED &&
                  !msg.isPermanentError &&
                  msg.attemptCount < maxRetries
              MessageLogFilter.PERMANENT_FAILED ->
                msg.currentStatus == MessageStatus.FAILED && msg.isPermanentError
              MessageLogFilter.SKIPPED -> msg.currentStatus == MessageStatus.SKIPPED
            }
          val matchesQuery =
            if (q.isEmpty()) true
            else {
              msg.recipientName.lowercase().contains(q) ||
                msg.recipientPhoneE164.lowercase().contains(q) ||
                (msg.whatsappMessageId?.lowercase()?.contains(q) == true) ||
                (msg.errorCode?.lowercase()?.contains(q) == true)
            }
          matchesCampaign && matchesStatus && matchesQuery
        }

      val selected =
        filtered.firstOrNull { it.id == params.selectedMsgId } ?: filtered.firstOrNull()
      if (selected != null && selectedMessageIdFlow.value != selected.id) {
        selectedMessageIdFlow.value = selected.id
      }

      val retryableCount =
        filtered.count {
          it.currentStatus == MessageStatus.FAILED &&
            !it.isPermanentError &&
            it.attemptCount < maxRetries
        }

      MessageLogsUiState(
        campaigns = campaigns,
        selectedCampaignIdFilter = params.campaignId,
        searchQuery = params.query,
        activeFilter = params.filter,
        filteredRecords = filtered,
        selectedRecord = selected,
        selectedRecordEvents = if (selected != null) events else emptyList(),
        selectedRecordRetries = if (selected != null) retries else emptyList(),
        retryableCountInView = retryableCount,
        maxRetryAttempts = maxRetries,
        feedbackMessage = feedback,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = MessageLogsUiState(),
    )

  private data class FilterParams(
    val query: String,
    val filter: MessageLogFilter,
    val campaignId: Long?,
    val selectedMsgId: Long?,
  )

  fun updateSearch(query: String) {
    searchQueryFlow.value = query
  }

  fun updateFilter(filter: MessageLogFilter) {
    statusFilterFlow.value = filter
  }

  fun updateCampaignFilter(campaignId: Long?) {
    campaignFilterFlow.value = campaignId
  }

  fun selectMessage(messageId: Long) {
    selectedMessageIdFlow.value = messageId
  }

  fun retrySingleMessage(messageId: Long) {
    viewModelScope.launch {
      campaignRepository
        .retrySingleMessage(messageId)
        .onSuccess { feedbackFlow.value = "Message re-queued for retry" }
        .onFailure { err -> feedbackFlow.value = err.message ?: "Retry blocked" }
    }
  }

  fun bulkRetryCampaign(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .retryEligibleFailedForCampaign(campaignId)
        .onSuccess { count ->
          feedbackFlow.value =
            if (count > 0) "Bulk retry started for $count messages"
            else "No eligible failed messages remain"
        }
        .onFailure { err -> feedbackFlow.value = err.message ?: "Bulk retry failed" }
    }
  }

  fun exportFilteredLogs(contentResolver: ContentResolver, uri: Uri) {
    viewModelScope.launch {
      val state = uiState.value
      if (state.filteredRecords.isEmpty()) {
        feedbackFlow.value = "No log records in current filter"
        return@launch
      }
      val namesById = state.campaigns.associate { it.id to it.name }
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            val csv = messageLogRepository.buildFilteredLogsCsv(state.filteredRecords, namesById)
            contentResolver.openOutputStream(uri)?.use { out ->
              out.write(csv.toByteArray(StandardCharsets.UTF_8))
              out.flush()
            } ?: error("Unable to open destination file")
          }
        }
      feedbackFlow.value =
        result.fold(
          onSuccess = { "Exported ${state.filteredRecords.size} message log rows" },
          onFailure = { "Export failed: ${it.message ?: "Unknown error"}" },
        )
    }
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(
    private val messageLogRepository: MessageLogRepository,
    private val campaignRepository: CampaignRepository,
    private val settingsRepository: SettingsRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MessageLogsViewModel(messageLogRepository, campaignRepository, settingsRepository) as T
  }
}
