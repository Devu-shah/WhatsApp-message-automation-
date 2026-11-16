package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SettingsRepository
import com.example.domain.model.DeviceKeyStatus
import com.example.domain.model.OperatorSettings
import com.example.domain.model.ProductionGateItem
import com.example.domain.model.SenderProfile
import com.example.domain.util.PhoneNumberHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val settings: OperatorSettings = OperatorSettings(),
  val deviceKeyStatus: DeviceKeyStatus = DeviceKeyStatus(),
  val senderProfiles: List<SenderProfile> = emptyList(),
  val completedSimulationsCount: Int = 0,
  val readinessChecklist: List<ProductionGateItem> = emptyList(),
  val isVerifyingBackend: Boolean = false,
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
) {
  val isProductionEligible: Boolean
    get() = readinessChecklist.isNotEmpty() && readinessChecklist.all { it.isVerified }
}

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

  private val deviceKeyStatusFlow = MutableStateFlow(DeviceKeyStatus())
  private val verifyingBackendFlow = MutableStateFlow(false)
  private val feedbackFlow = MutableStateFlow<Pair<String, Boolean>?>(null)

  val uiState: StateFlow<SettingsUiState> =
    combine(
        combine(
          settingsRepository.settings,
          settingsRepository.senderProfiles,
          settingsRepository.completedSimulationCount,
        ) { settings, senders, simCount ->
          Triple(settings, senders, simCount)
        },
        combine(deviceKeyStatusFlow, verifyingBackendFlow, feedbackFlow) {
          keyStatus,
          verifying,
          feedback ->
          Triple(keyStatus, verifying, feedback)
        },
      ) { (settings, senders, simCount), (keyStatus, verifying, feedback) ->
        val gate =
          settingsRepository.evaluateProductionReadinessGate(
            settings = settings,
            keyStatus = keyStatus,
            senderProfiles = senders,
            completedSimulationsCount = simCount,
          )
        SettingsUiState(
          settings = settings,
          deviceKeyStatus = keyStatus,
          senderProfiles = senders,
          completedSimulationsCount = simCount,
          readinessChecklist = gate,
          isVerifyingBackend = verifying,
          feedbackMessage = feedback?.first,
          isError = feedback?.second ?: false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
      )

  init {
    refreshDeviceKey()
  }

  fun refreshDeviceKey() {
    viewModelScope.launch {
      deviceKeyStatusFlow.value = settingsRepository.getOrCreateDeviceKeyStatus()
    }
  }

  fun rotateDeviceKey() {
    viewModelScope.launch {
      val status = settingsRepository.rotateDeviceKey()
      deviceKeyStatusFlow.value = status
      feedbackFlow.value =
        if (status.isReady) {
          "Rotated Android Keystore ECDSA P-256 key" to false
        } else {
          (status.errorMessage ?: "Unable to rotate device key") to true
        }
    }
  }

  fun saveDefaultCountryCode(code: String) {
    viewModelScope.launch {
      settingsRepository
        .updateDefaultCountryCode(code)
        .onSuccess {
          feedbackFlow.value = "Default calling code updated to ${code.trim()}" to false
        }
        .onFailure {
          feedbackFlow.value = (it.message ?: "Invalid calling code") to true
        }
    }
  }

  fun toggleAutoApplyDefaultCode(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.updateAutoApplyDefaultCode(enabled)
      feedbackFlow.value =
        if (enabled) {
          "Auto-apply default calling code enabled for CSV numbers without '+'" to false
        } else {
          "Auto-apply disabled — CSV numbers must include an explicit '+' country code" to false
        }
    }
  }

  fun toggleSimulationMode(enabled: Boolean) {
    viewModelScope.launch {
      if (!enabled && !uiState.value.isProductionEligible) {
        feedbackFlow.value =
          "Production Mode remains locked until all 12 Readiness Gate checks pass." to true
        return@launch
      }
      settingsRepository.updateSimulationMode(enabled)
    }
  }

  fun saveCloudRunBaseUrl(url: String) {
    viewModelScope.launch {
      settingsRepository
        .updateCloudRunBaseUrl(url)
        .onSuccess { feedbackFlow.value = "Cloud Run endpoint saved" to false }
        .onFailure { feedbackFlow.value = (it.message ?: "Invalid HTTPS URL") to true }
    }
  }

  fun saveBatchAndRetrySettings(batchSize: Int, maxRetries: Int) {
    viewModelScope.launch {
      settingsRepository.updateBatchSize(batchSize)
      settingsRepository.updateMaxRetryAttempts(maxRetries)
      feedbackFlow.value = "Processing & retry preferences saved" to false
    }
  }

  fun saveOperatorTestNumber(rawNumber: String) {
    viewModelScope.launch {
      val current = uiState.value.settings
      settingsRepository
        .updateOperatorTestNumber(
          rawNumber = rawNumber,
          defaultCode = current.defaultCountryCode,
          autoApplyDefault = current.autoApplyDefaultCountryCode,
        )
        .onSuccess { normalized ->
          feedbackFlow.value =
            if (normalized.isEmpty()) "Cleared operator test number" to false
            else "Saved test WhatsApp number $normalized" to false
        }
        .onFailure { feedbackFlow.value = (it.message ?: "Invalid phone number") to true }
    }
  }

  fun toggleNotifications(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.updateNotificationsEnabled(enabled) }
  }

  fun addSenderProfile(
    label: String,
    rawPhoneNumber: String,
    cloudRunProfileRef: String,
    makeDefault: Boolean,
  ) {
    viewModelScope.launch {
      val current = uiState.value.settings
      settingsRepository
        .addSenderProfile(
          label = label,
          rawPhoneNumber = rawPhoneNumber,
          cloudRunProfileRef = cloudRunProfileRef,
          defaultCode = current.defaultCountryCode,
          autoApplyDefault = current.autoApplyDefaultCountryCode,
          makeDefault = makeDefault,
        )
        .onSuccess {
          feedbackFlow.value = "Saved WhatsApp Business contact number" to false
        }
        .onFailure { feedbackFlow.value = (it.message ?: "Unable to add sender number") to true }
    }
  }

  fun setDefaultSenderProfile(profileId: Long) {
    viewModelScope.launch {
      settingsRepository.setDefaultSenderProfile(profileId)
      feedbackFlow.value = "Updated active WhatsApp Business sender number" to false
    }
  }

  fun deleteSenderProfile(profileId: Long) {
    viewModelScope.launch {
      settingsRepository.deleteSenderProfile(profileId)
      feedbackFlow.value = "Removed WhatsApp Business sender profile" to false
    }
  }

  fun toggleGcpBudgetConfirmed(confirmed: Boolean) {
    viewModelScope.launch {
      settingsRepository.updateGcpBudgetAlertConfirmed(confirmed)
      feedbackFlow.value =
        if (confirmed) "GCP Billing budget alert confirmation saved" to false
        else "GCP Billing budget alert confirmation cleared" to false
    }
  }

  fun registerDeviceAndVerify(bootstrapToken: String?) {
    viewModelScope.launch {
      val baseUrl = uiState.value.settings.cloudRunBaseUrl
      if (!baseUrl.startsWith("https://")) {
        feedbackFlow.value = "Save an HTTPS Google Cloud Run URL first" to true
        return@launch
      }
      verifyingBackendFlow.value = true
      settingsRepository
        .registerDeviceAndVerifyCloudRun(baseUrl, bootstrapToken)
        .onSuccess { resp ->
          feedbackFlow.value =
            if (resp.ok && resp.deviceRegistered)
              "Tablet ECDSA key registered & Cloud Run readiness verified" to false
            else "Registered key; Cloud Run reports incomplete server secrets" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Cloud Run registration failed") to true
        }
      verifyingBackendFlow.value = false
    }
  }

  fun verifyCloudRunReadiness() {
    viewModelScope.launch {
      val baseUrl = uiState.value.settings.cloudRunBaseUrl
      if (!baseUrl.startsWith("https://")) {
        feedbackFlow.value = "Save an HTTPS Google Cloud Run URL first" to true
        return@launch
      }
      verifyingBackendFlow.value = true
      settingsRepository
        .verifyCloudRunReadiness(baseUrl)
        .onSuccess { resp ->
          feedbackFlow.value =
            if (resp.ok && resp.metaSecretsConfigured && resp.webhookVerifyConfigured)
              "Cloud Run & Meta readiness check passed" to false
            else "Cloud Run reachable; check pending items in Readiness Gate" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Unable to verify Cloud Run readiness") to true
        }
      verifyingBackendFlow.value = false
    }
  }

  fun previewPhoneNormalization(sampleInput: String): PhoneNumberHelper.NormalizationResult {
    val current = uiState.value.settings
    return PhoneNumberHelper.normalize(
      rawInput = sampleInput,
      defaultCountryCode = current.defaultCountryCode,
      autoApplyDefaultCode = current.autoApplyDefaultCountryCode,
    )
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      SettingsViewModel(settingsRepository) as T
  }
}
