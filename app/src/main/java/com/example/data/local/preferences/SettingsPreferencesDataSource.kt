package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.OperatorSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reachHubDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "reachhub_operator_prefs")

class SettingsPreferencesDataSource(private val context: Context) {

  val settingsFlow: Flow<OperatorSettings> =
    context.reachHubDataStore.data.map { prefs ->
      OperatorSettings(
        defaultCountryCode = prefs[KEY_DEFAULT_COUNTRY_CODE] ?: "+91",
        autoApplyDefaultCountryCode = prefs[KEY_AUTO_APPLY_DEFAULT_CODE] ?: true,
        simulationModeEnabled = prefs[KEY_SIMULATION_MODE] ?: true,
        cloudRunBaseUrl = prefs[KEY_CLOUD_RUN_URL] ?: "",
        batchSize = prefs[KEY_BATCH_SIZE] ?: 50,
        maxRetryAttempts = prefs[KEY_MAX_RETRIES] ?: 3,
        operatorTestNumber = prefs[KEY_OPERATOR_TEST_NUMBER] ?: "",
        notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
        dataRetentionDays = prefs[KEY_DATA_RETENTION_DAYS] ?: 180,
        cloudRunDeviceRegistered = prefs[KEY_CLOUD_RUN_DEVICE_REGISTERED] ?: false,
        cloudRunMetaSecretsVerified = prefs[KEY_CLOUD_RUN_META_SECRETS_VERIFIED] ?: false,
        cloudRunWebhookVerified = prefs[KEY_CLOUD_RUN_WEBHOOK_VERIFIED] ?: false,
        cloudRunQueueConfigured = prefs[KEY_CLOUD_RUN_QUEUE_CONFIGURED] ?: false,
        gcpBudgetAlertConfirmed = prefs[KEY_GCP_BUDGET_ALERT_CONFIRMED] ?: false,
        lastReadinessCheckedAt = prefs[KEY_LAST_READINESS_CHECKED_AT],
      )
    }

  suspend fun updateDefaultCountryCode(code: String) {
    context.reachHubDataStore.edit { it[KEY_DEFAULT_COUNTRY_CODE] = code.trim() }
  }

  suspend fun updateAutoApplyDefaultCode(enabled: Boolean) {
    context.reachHubDataStore.edit { it[KEY_AUTO_APPLY_DEFAULT_CODE] = enabled }
  }

  suspend fun updateSimulationMode(enabled: Boolean) {
    context.reachHubDataStore.edit { it[KEY_SIMULATION_MODE] = enabled }
  }

  suspend fun updateCloudRunBaseUrl(url: String) {
    context.reachHubDataStore.edit { prefs ->
      val trimmed = url.trim()
      if (prefs[KEY_CLOUD_RUN_URL] != trimmed) {
        prefs[KEY_CLOUD_RUN_URL] = trimmed
        prefs[KEY_CLOUD_RUN_DEVICE_REGISTERED] = false
        prefs[KEY_CLOUD_RUN_META_SECRETS_VERIFIED] = false
        prefs[KEY_CLOUD_RUN_WEBHOOK_VERIFIED] = false
        prefs[KEY_CLOUD_RUN_QUEUE_CONFIGURED] = false
        prefs.remove(KEY_LAST_READINESS_CHECKED_AT)
      }
    }
  }

  suspend fun updateBatchSize(batchSize: Int) {
    context.reachHubDataStore.edit { it[KEY_BATCH_SIZE] = batchSize.coerceIn(10, 200) }
  }

  suspend fun updateMaxRetryAttempts(attempts: Int) {
    context.reachHubDataStore.edit { it[KEY_MAX_RETRIES] = attempts.coerceIn(0, 5) }
  }

  suspend fun updateOperatorTestNumber(number: String) {
    context.reachHubDataStore.edit { it[KEY_OPERATOR_TEST_NUMBER] = number.trim() }
  }

  suspend fun updateNotificationsEnabled(enabled: Boolean) {
    context.reachHubDataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
  }

  suspend fun updateDataRetentionDays(days: Int) {
    context.reachHubDataStore.edit { it[KEY_DATA_RETENTION_DAYS] = days.coerceIn(7, 3650) }
  }

  suspend fun updateCloudRunReadiness(
    deviceRegistered: Boolean,
    metaSecretsVerified: Boolean,
    webhookVerified: Boolean,
    queueConfigured: Boolean,
    checkedAt: Long,
  ) {
    context.reachHubDataStore.edit { prefs ->
      prefs[KEY_CLOUD_RUN_DEVICE_REGISTERED] = deviceRegistered
      prefs[KEY_CLOUD_RUN_META_SECRETS_VERIFIED] = metaSecretsVerified
      prefs[KEY_CLOUD_RUN_WEBHOOK_VERIFIED] = webhookVerified
      prefs[KEY_CLOUD_RUN_QUEUE_CONFIGURED] = queueConfigured
      prefs[KEY_LAST_READINESS_CHECKED_AT] = checkedAt
    }
  }

  suspend fun updateGcpBudgetAlertConfirmed(confirmed: Boolean) {
    context.reachHubDataStore.edit { it[KEY_GCP_BUDGET_ALERT_CONFIRMED] = confirmed }
  }

  private companion object {
    val KEY_DEFAULT_COUNTRY_CODE = stringPreferencesKey("default_country_code")
    val KEY_AUTO_APPLY_DEFAULT_CODE = booleanPreferencesKey("auto_apply_default_code")
    val KEY_SIMULATION_MODE = booleanPreferencesKey("simulation_mode_enabled")
    val KEY_CLOUD_RUN_URL = stringPreferencesKey("cloud_run_base_url")
    val KEY_BATCH_SIZE = intPreferencesKey("batch_size")
    val KEY_MAX_RETRIES = intPreferencesKey("max_retry_attempts")
    val KEY_OPERATOR_TEST_NUMBER = stringPreferencesKey("operator_test_number")
    val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val KEY_DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")
    val KEY_CLOUD_RUN_DEVICE_REGISTERED = booleanPreferencesKey("cloud_run_device_registered")
    val KEY_CLOUD_RUN_META_SECRETS_VERIFIED =
      booleanPreferencesKey("cloud_run_meta_secrets_verified")
    val KEY_CLOUD_RUN_WEBHOOK_VERIFIED = booleanPreferencesKey("cloud_run_webhook_verified")
    val KEY_CLOUD_RUN_QUEUE_CONFIGURED = booleanPreferencesKey("cloud_run_queue_configured")
    val KEY_GCP_BUDGET_ALERT_CONFIRMED = booleanPreferencesKey("gcp_budget_alert_confirmed")
    val KEY_LAST_READINESS_CHECKED_AT =
      androidx.datastore.preferences.core.longPreferencesKey("last_readiness_checked_at")
  }
}
