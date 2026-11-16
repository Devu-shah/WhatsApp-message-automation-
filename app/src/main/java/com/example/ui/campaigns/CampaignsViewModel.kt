package com.example.ui.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.repository.CampaignRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.MessageLogRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TemplateRepository
import com.example.domain.model.AudienceSelectionMode
import com.example.domain.model.CampaignStatsSummary
import com.example.domain.model.ContactWithFields
import com.example.domain.model.MediaAsset
import com.example.domain.model.MessageTemplate
import com.example.domain.model.OperatorSettings
import com.example.domain.model.PreDispatchAudit
import com.example.domain.model.SenderProfile
import com.example.domain.util.CampaignEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CampaignsUiState(
  val campaigns: List<CampaignStatsSummary> = emptyList(),
  val selectedCampaignId: Long? = null,
  val selectedCampaignMessages: List<MessageRecordEntity> = emptyList(),
  val templates: List<MessageTemplate> = emptyList(),
  val mediaAssets: List<MediaAsset> = emptyList(),
  val senderProfiles: List<SenderProfile> = emptyList(),
  val contacts: List<ContactWithFields> = emptyList(),
  val suppressedPhones: Set<String> = emptySet(),
  val settings: OperatorSettings = OperatorSettings(),
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
) {
  val selectedSummary: CampaignStatsSummary?
    get() = campaigns.firstOrNull { it.campaign.id == selectedCampaignId } ?: campaigns.firstOrNull()
}

class CampaignsViewModel(
  private val campaignRepository: CampaignRepository,
  contactRepository: ContactRepository,
  templateRepository: TemplateRepository,
  mediaRepository: MediaRepository,
  messageLogRepository: MessageLogRepository,
  settingsRepository: SettingsRepository,
) : ViewModel() {

  private val selectedIdFlow = MutableStateFlow<Long?>(null)
  private val feedbackFlow = MutableStateFlow<Pair<String, Boolean>?>(null)

  private val assetsCombineFlow =
    combine(
      templateRepository.templates,
      mediaRepository.mediaAssets,
      settingsRepository.senderProfiles,
      settingsRepository.settings,
    ) { templates, media, senders, settings ->
      AssetsBundle(templates, media, senders, settings)
    }

  private val contactsCombineFlow =
    combine(
      contactRepository.contactsWithFields,
      contactRepository.suppressionList,
    ) { contacts, suppressions ->
      contacts to suppressions.map { it.phoneE164 }.toSet()
    }

  val uiState: StateFlow<CampaignsUiState> =
    combine(
      campaignRepository.campaignSummaries,
      messageLogRepository.allMessages,
      assetsCombineFlow,
      contactsCombineFlow,
      combine(selectedIdFlow, feedbackFlow) { sel, fb -> sel to fb },
    ) { summaries, allMessages, assets, (contacts, suppressed), (selectedId, feedback) ->
      val resolvedId = selectedId ?: summaries.firstOrNull()?.campaign?.id
      val selectedMsgs =
        if (resolvedId != null) allMessages.filter { it.campaignId == resolvedId } else emptyList()
      CampaignsUiState(
        campaigns = summaries,
        selectedCampaignId = resolvedId,
        selectedCampaignMessages = selectedMsgs,
        templates = assets.templates,
        mediaAssets = assets.media,
        senderProfiles = assets.senders,
        contacts = contacts,
        suppressedPhones = suppressed,
        settings = assets.settings,
        feedbackMessage = feedback?.first,
        isError = feedback?.second ?: false,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = CampaignsUiState(),
    )

  private data class AssetsBundle(
    val templates: List<MessageTemplate>,
    val media: List<MediaAsset>,
    val senders: List<SenderProfile>,
    val settings: OperatorSettings,
  )

  fun selectCampaign(campaignId: Long) {
    selectedIdFlow.value = campaignId
  }

  fun computeAudit(
    template: MessageTemplate?,
    audienceMode: AudienceSelectionMode,
    filterQuery: String,
    selectedContactIds: Set<Long>?,
  ): PreDispatchAudit {
    val state = uiState.value
    return CampaignEngine.evaluatePreDispatchAudit(
      contacts = state.contacts,
      suppressedPhones = state.suppressedPhones,
      template = template,
      audienceMode = audienceMode,
      audienceFilterQuery = filterQuery,
      selectedContactIds = selectedContactIds,
      defaultCountryCode = state.settings.defaultCountryCode,
      autoApplyDefaultCode = state.settings.autoApplyDefaultCountryCode,
    )
  }

  fun createAndLaunchCampaign(
    name: String,
    description: String,
    template: MessageTemplate,
    mediaAsset: MediaAsset?,
    senderProfile: SenderProfile,
    audit: PreDispatchAudit,
    launchImmediately: Boolean,
  ) {
    viewModelScope.launch {
      campaignRepository
        .createAndOptionallyLaunchCampaign(
          name = name,
          description = description,
          template = template,
          mediaAsset = mediaAsset,
          senderProfile = senderProfile,
          audit = audit,
          launchImmediately = launchImmediately,
        )
        .onSuccess { id ->
          selectedIdFlow.value = id
          val actionLabel = if (launchImmediately) "launched" else "saved as Ready"
          feedbackFlow.value = "Campaign '$name' $actionLabel (${audit.validEligibleCount} recipients)" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Failed to create campaign") to true
        }
    }
  }

  fun launchDraft(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .launchDraftCampaign(campaignId)
        .onSuccess { feedbackFlow.value = "Campaign started" to false }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Unable to start draft") to true }
    }
  }

  fun pauseCampaign(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .pauseCampaign(campaignId)
        .onSuccess { feedbackFlow.value = "Campaign paused cleanly" to false }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Unable to pause") to true }
    }
  }

  fun resumeCampaign(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .resumeCampaign(campaignId)
        .onSuccess { feedbackFlow.value = "Campaign resumed" to false }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Unable to resume") to true }
    }
  }

  fun cancelCampaign(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .cancelCampaign(campaignId)
        .onSuccess { feedbackFlow.value = "Campaign cancelled; remaining queued items skipped" to false }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Unable to cancel") to true }
    }
  }

  fun retryEligibleFailed(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .retryEligibleFailedForCampaign(campaignId)
        .onSuccess { count ->
          feedbackFlow.value =
            if (count > 0) "Re-queued $count retryable failed messages" to false
            else "No retryable temporary failures eligible for retry" to false
        }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Retry failed") to true }
    }
  }

  fun syncWebhooksFromCloudRun(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .syncCampaignStatusesFromCloudRun(campaignId)
        .onSuccess { count ->
          feedbackFlow.value = "Synced $count webhook status events from Cloud Run" to false
        }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Webhook sync failed") to true }
    }
  }

  fun deleteCampaign(campaignId: Long) {
    viewModelScope.launch {
      campaignRepository
        .deleteCampaign(campaignId)
        .onSuccess {
          if (selectedIdFlow.value == campaignId) selectedIdFlow.value = null
          feedbackFlow.value = "Deleted campaign" to false
        }
        .onFailure { err -> feedbackFlow.value = (err.message ?: "Cannot delete campaign") to true }
    }
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(
    private val campaignRepository: CampaignRepository,
    private val contactRepository: ContactRepository,
    private val templateRepository: TemplateRepository,
    private val mediaRepository: MediaRepository,
    private val messageLogRepository: MessageLogRepository,
    private val settingsRepository: SettingsRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CampaignsViewModel(
        campaignRepository = campaignRepository,
        contactRepository = contactRepository,
        templateRepository = templateRepository,
        mediaRepository = mediaRepository,
        messageLogRepository = messageLogRepository,
        settingsRepository = settingsRepository,
      ) as T
  }
}
