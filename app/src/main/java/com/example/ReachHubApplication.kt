package com.example

import android.app.Application
import com.example.data.local.db.ReachHubDatabase
import com.example.data.local.preferences.SettingsPreferencesDataSource
import com.example.data.remote.CloudRunApiClient
import com.example.data.repository.CampaignRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.DashboardRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.MessageLogRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TemplateRepository
import com.example.security.AndroidKeystoreDeviceKeyManager
import com.example.security.DeviceKeyManager
import com.example.workers.CampaignExecutionWorker

class AppContainer(application: Application) {
  private val database: ReachHubDatabase by lazy { ReachHubDatabase.getInstance(application) }

  private val preferencesDataSource: SettingsPreferencesDataSource by lazy {
    SettingsPreferencesDataSource(application)
  }

  private val deviceKeyManager: DeviceKeyManager by lazy { AndroidKeystoreDeviceKeyManager() }

  val cloudRunApiClient: CloudRunApiClient by lazy { CloudRunApiClient(deviceKeyManager) }

  val dashboardRepository: DashboardRepository by lazy {
    DashboardRepository(database.dashboardDao())
  }

  val contactRepository: ContactRepository by lazy { ContactRepository(database.contactDao()) }

  val templateRepository: TemplateRepository by lazy { TemplateRepository(database.templateDao()) }

  val mediaRepository: MediaRepository by lazy { MediaRepository(database.mediaDao()) }

  val campaignRepository: CampaignRepository by lazy {
    CampaignRepository(
      campaignDao = database.campaignDao(),
      contactDao = database.contactDao(),
      templateDao = database.templateDao(),
      mediaDao = database.mediaDao(),
      messageLogDao = database.messageLogDao(),
      senderProfileDao = database.senderProfileDao(),
      preferences = preferencesDataSource,
      cloudRunApiClient = cloudRunApiClient,
      onEnqueueDispatch = { campaignId, delayMs ->
        CampaignExecutionWorker.enqueue(application, campaignId, delayMs)
      },
      onCancelDispatch = { campaignId ->
        CampaignExecutionWorker.cancel(application, campaignId)
      },
    )
  }

  val messageLogRepository: MessageLogRepository by lazy {
    MessageLogRepository(database.messageLogDao())
  }

  val settingsRepository: SettingsRepository by lazy {
    SettingsRepository(
      preferences = preferencesDataSource,
      senderProfileDao = database.senderProfileDao(),
      deviceKeyManager = deviceKeyManager,
      campaignDao = database.campaignDao(),
      cloudRunApiClient = cloudRunApiClient,
    )
  }
}

class ReachHubApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
