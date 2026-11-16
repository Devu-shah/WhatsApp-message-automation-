package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.ReachHubDatabase
import com.example.data.local.db.entity.ContactEntity
import com.example.data.local.db.entity.SuppressionEntity
import com.example.data.local.preferences.SettingsPreferencesDataSource
import com.example.data.remote.CloudRunApiClient
import com.example.data.repository.CampaignRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TemplateRepository
import com.example.domain.model.AppMode
import com.example.domain.model.AudienceSelectionMode
import com.example.domain.model.CampaignStatus
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactWithFields
import com.example.domain.model.DeviceKeyStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.OperatorSettings
import com.example.domain.model.RetryPolicyClassifier
import com.example.domain.model.SenderProfile
import com.example.domain.util.CampaignEngine
import com.example.domain.util.CsvParserAndSecurity
import com.example.domain.util.TemplateEngine
import com.example.security.DeviceKeyManager
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CampaignAndRetryTest {

  private lateinit var context: Context
  private lateinit var db: ReachHubDatabase
  private lateinit var prefs: SettingsPreferencesDataSource
  private lateinit var fakeKeyManager: FakeEcdsaDeviceKeyManager
  private lateinit var campaignRepository: CampaignRepository
  private lateinit var contactRepository: ContactRepository
  private lateinit var templateRepository: TemplateRepository
  private lateinit var settingsRepository: SettingsRepository

  private val enqueuedCampaignIds = mutableListOf<Long>()
  private val cancelledCampaignIds = mutableListOf<Long>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, ReachHubDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    prefs = SettingsPreferencesDataSource(context)
    fakeKeyManager = FakeEcdsaDeviceKeyManager()
    val apiClient = CloudRunApiClient(fakeKeyManager)

    contactRepository = ContactRepository(db.contactDao())
    templateRepository = TemplateRepository(db.templateDao())
    settingsRepository =
      SettingsRepository(
        preferences = prefs,
        senderProfileDao = db.senderProfileDao(),
        deviceKeyManager = fakeKeyManager,
        campaignDao = db.campaignDao(),
        cloudRunApiClient = apiClient,
      )
    campaignRepository =
      CampaignRepository(
        campaignDao = db.campaignDao(),
        contactDao = db.contactDao(),
        templateDao = db.templateDao(),
        mediaDao = db.mediaDao(),
        messageLogDao = db.messageLogDao(),
        senderProfileDao = db.senderProfileDao(),
        preferences = prefs,
        cloudRunApiClient = apiClient,
        onEnqueueDispatch = { cid, _ -> enqueuedCampaignIds.add(cid) },
        onCancelDispatch = { cid -> cancelledCampaignIds.add(cid) },
      )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `pre-dispatch audit classifies valid invalid duplicate opted-out and builds deterministic keys`() {
    val fields = TemplateEngine.standardContactFieldKeys
    val mappings = TemplateEngine.syncMappings("Hi {Name} from {City}", emptyList(), fields)
    val template =
      com.example.domain.model.MessageTemplate(
        id = 1L,
        name = "Welcome",
        metaTemplateName = "welcome_city",
        languageCode = "en",
        category = "MARKETING",
        bodyText = "Hi {Name} from {City}",
        placeholders = listOf("Name", "City"),
        mappings = mappings,
        headerMediaRequired = false,
        validationStatus = com.example.domain.model.TemplateValidationStatus.VALID,
        createdAt = 1L,
        updatedAt = 1L,
      )

    val contacts =
      listOf(
        ContactWithFields(
          id = 1L,
          name = "Ananya Sharma",
          phoneE164 = "+919876543210",
          rawPhone = "9876543210",
          email = null,
          college = null,
          city = "Bengaluru",
          state = null,
          consentStatus = ConsentStatus.GRANTED,
          isOptedOut = false,
          customFields = emptyMap(),
        ),
        ContactWithFields(
          id = 2L,
          name = "Duplicate Phone",
          phoneE164 = "+919876543210",
          rawPhone = "9876543210",
          email = null,
          college = null,
          city = "Pune",
          state = null,
          consentStatus = ConsentStatus.GRANTED,
          isOptedOut = false,
          customFields = emptyMap(),
        ),
        ContactWithFields(
          id = 3L,
          name = "Opted Out",
          phoneE164 = "+919811122233",
          rawPhone = "9811122233",
          email = null,
          college = null,
          city = "Delhi",
          state = null,
          consentStatus = ConsentStatus.REVOKED,
          isOptedOut = true,
          customFields = emptyMap(),
        ),
        ContactWithFields(
          id = 4L,
          name = "Bad Phone",
          phoneE164 = "123",
          rawPhone = "123",
          email = null,
          college = null,
          city = null,
          state = null,
          consentStatus = ConsentStatus.GRANTED,
          isOptedOut = false,
          customFields = emptyMap(),
        ),
      )

    val audit =
      CampaignEngine.evaluatePreDispatchAudit(
        contacts = contacts,
        suppressedPhones = emptySet(),
        template = template,
        audienceMode = AudienceSelectionMode.ALL_ELIGIBLE,
        audienceFilterQuery = "",
        selectedContactIds = null,
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
      )

    assertEquals(4, audit.totalEvaluated)
    assertEquals(1, audit.validEligibleCount)
    assertEquals(1, audit.invalidPhoneCount)
    assertEquals(1, audit.duplicatePhoneCount)
    assertEquals(1, audit.optedOutSuppressedCount)
    assertTrue(audit.sampleRenderedMessage.contains("Ananya Sharma"))

    val k1 = CampaignEngine.buildIdempotencyKey(42L, 1L, "+919876543210")
    val k2 = CampaignEngine.buildIdempotencyKey(42L, 1L, "+919876543210")
    assertEquals(k1, k2)
  }

  @Test
  fun `retry classifier distinguishes permanent vs temporary errors and computes backoff`() {
    val perm = RetryPolicyClassifier.classify("131026", "Receiver unreachable")
    val temp = RetryPolicyClassifier.classify("130429", "Throughput exceeded")
    assertTrue(perm.isPermanent)
    assertFalse(temp.isPermanent)

    assertEquals(30_000L, RetryPolicyClassifier.computeBackoffDelayMs(1))
    assertEquals(120_000L, RetryPolicyClassifier.computeBackoffDelayMs(2))
    assertEquals(600_000L, RetryPolicyClassifier.computeBackoffDelayMs(3))
    assertEquals(1_800_000L, RetryPolicyClassifier.computeBackoffDelayMs(4))
  }

  @Test
  fun `simulation dispatch, retry eligibility, monotonic status guard, and safe draft clone work end to end`() =
    runTest {
      prefs.updateSimulationMode(true)
      val senderId =
        settingsRepository
          .addSenderProfile(
            label = "Admissions Desk",
            rawPhoneNumber = "9876500001",
            cloudRunProfileRef = "admissions",
            defaultCode = "+91",
            autoApplyDefault = true,
            makeDefault = true,
          )
          .getOrThrow()
      val sender = settingsRepository.senderProfiles.first().first { it.id == senderId }

      val fields = TemplateEngine.standardContactFieldKeys
      val body = "Hello {Name}, campus tour at {City}"
      val templateId =
        templateRepository
          .saveTemplate(
            id = 0,
            name = "Campus Tour",
            metaTemplateName = "campus_tour_v1",
            languageCode = "en",
            category = "MARKETING",
            bodyText = body,
            mappings = TemplateEngine.syncMappings(body, emptyList(), fields),
            headerMediaRequired = false,
            availableFields = fields,
          )
          .getOrThrow()
      val template = templateRepository.templates.first().first { it.id == templateId }

      // Insert 4 contacts: normal, temp rate limit failure, permanent failure, and mid-flight suppressed
      db.contactDao().upsertContact(
        ContactEntity(name = "Kabir Sen", phoneE164 = "+919800000011", rawPhone = "9800000011")
      )
      db.contactDao().upsertContact(
        ContactEntity(name = "Meera rate_limit", phoneE164 = "+919800000012", rawPhone = "9800000012")
      )
      db.contactDao().upsertContact(
        ContactEntity(name = "Raju invalid_wa", phoneE164 = "+919800000013", rawPhone = "9800000013")
      )
      db.contactDao().upsertContact(
        ContactEntity(name = "Later OptOut", phoneE164 = "+919800000014", rawPhone = "9800000014")
      )

      val allContacts = contactRepository.contactsWithFields.first()
      val audit =
        CampaignEngine.evaluatePreDispatchAudit(
          contacts = allContacts,
          suppressedPhones = emptySet(),
          template = template,
          audienceMode = AudienceSelectionMode.ALL_ELIGIBLE,
          audienceFilterQuery = "",
          selectedContactIds = null,
          defaultCountryCode = "+91",
          autoApplyDefaultCode = true,
        )

      val campaignId =
        campaignRepository
          .createAndOptionallyLaunchCampaign(
            name = "Spring Open House",
            description = "Simulation test wave",
            template = template,
            mediaAsset = null,
            senderProfile = sender,
            audit = audit,
            launchImmediately = true,
          )
          .getOrThrow()

      assertTrue(enqueuedCampaignIds.contains(campaignId))

      // Add mid-flight suppression before worker executes
      db.contactDao()
        .addSuppression(
          SuppressionEntity(phoneE164 = "+919800000014", reason = "STOP", source = "test")
        )

      campaignRepository.executeCampaignDispatchLoop(campaignId, simulationBatchDelayMs = 0L).getOrThrow()

      val summary = campaignRepository.campaignSummaries.first().first { it.campaign.id == campaignId }
      assertEquals(CampaignStatus.COMPLETED, summary.campaign.status)
      assertEquals(AppMode.SIMULATION, summary.campaign.mode)
      assertEquals(4, summary.totalRecipients)
      assertEquals(1, summary.readCount) // Kabir Sen
      assertEquals(1, summary.skippedCount) // Later OptOut
      assertEquals(2, summary.failedCount) // rate_limit + invalid_wa
      assertEquals(1, summary.retryableFailedCount)
      assertEquals(1, summary.permanentFailedCount)

      val records = db.campaignDao().getMessageRecordsForCampaign(campaignId)
      val kabirRecord = records.first { it.recipientName == "Kabir Sen" }
      val permRecord = records.first { it.recipientName == "Raju invalid_wa" }
      val tempRecord = records.first { it.recipientName == "Meera rate_limit" }

      // Monotonic guard: late SENT event cannot downgrade READ
      db.messageLogDao()
        .applyMonotonicStatusTransition(
          messageId = kabirRecord.id,
          targetStatus = MessageStatus.SENT,
          eventTimestamp = System.currentTimeMillis() + 500L,
          source = "late_webhook",
          detail = "Out of order",
        )
      assertEquals(MessageStatus.READ, db.messageLogDao().getMessageRecordById(kabirRecord.id)?.currentStatus)

      // Permanent failure retry is blocked
      assertTrue(campaignRepository.retrySingleMessage(permRecord.id).isFailure)

      // Temporary failure bulk retry re-queues Meera rate_limit and completes cleanly
      val requeued = campaignRepository.retryEligibleFailedForCampaign(campaignId).getOrThrow()
      assertEquals(1, requeued)
      campaignRepository.executeCampaignDispatchLoop(campaignId, simulationBatchDelayMs = 0L).getOrThrow()

      val afterRetryMeera = db.messageLogDao().getMessageRecordById(tempRecord.id)
      assertEquals(MessageStatus.READ, afterRetryMeera?.currentStatus)
      assertEquals(2, afterRetryMeera?.attemptCount)

      // Pause & resume checks
      campaignRepository.pauseCampaign(campaignId).getOrThrow()
      assertEquals(CampaignStatus.PAUSED, db.campaignDao().getCampaignById(campaignId)?.status)
      assertTrue(cancelledCampaignIds.contains(campaignId))

      // Clone as safe DRAFT creates zero MessageRecordEntity rows
      val clonedId = campaignRepository.cloneCampaignAsSafeDraft(campaignId).getOrThrow()
      val clonedCampaign = db.campaignDao().getCampaignById(clonedId)
      assertEquals(CampaignStatus.DRAFT, clonedCampaign?.status)
      assertTrue(db.campaignDao().getMessageRecordsForCampaign(clonedId).isEmpty())
    }

  @Test
  fun `report and message log CSV exports neutralize formula injection`() {
    val summary =
      com.example.domain.model.CampaignStatsSummary(
        campaign =
          com.example.data.local.db.entity.CampaignEntity(
            id = 5L,
            name = "=SUM(A1:A2)",
            templateId = 1L,
            senderPhoneSnapshot = "+919876500001",
            status = CampaignStatus.COMPLETED,
          ),
        templateName = "Promo",
        mediaName = null,
        totalRecipients = 1,
        queuedCount = 0,
        submittedCount = 0,
        sentCount = 0,
        deliveredCount = 1,
        readCount = 0,
        failedCount = 0,
        retryableFailedCount = 0,
        permanentFailedCount = 0,
        skippedCount = 0,
      )
    val record =
      com.example.data.local.db.entity.MessageRecordEntity(
        id = 10L,
        campaignId = 5L,
        contactId = 1L,
        recipientName = "@cmd('calc')",
        recipientPhoneE164 = "+919876543210",
        personalizedBody = "Hello",
        idempotencyKey = "rh_5_1_abc",
        currentStatus = MessageStatus.DELIVERED,
      )

    val reportCsv = CsvParserAndSecurity.buildCampaignReportCsv(summary, listOf(record))
    assertTrue(reportCsv.contains("\"'=SUM(A1:A2)\""))
    assertTrue(reportCsv.contains("\"'@cmd('calc')\""))
    assertTrue(reportCsv.contains("\"'+919876543210\""))
  }

  @Test
  fun `production readiness gate enforces all 12 checks and CloudRunApiClient signs requests with ECDSA P-256`() =
    runTest {
      val incomplete =
        settingsRepository.evaluateProductionReadinessGate(
          settings = OperatorSettings(),
          keyStatus = fakeKeyManager.getOrCreateStatus(),
          senderProfiles = emptyList(),
          completedSimulationsCount = 0,
        )
      assertEquals(12, incomplete.size)
      assertFalse(incomplete.all { it.isVerified })

      val complete =
        settingsRepository.evaluateProductionReadinessGate(
          settings =
            OperatorSettings(
              cloudRunBaseUrl = "https://reachhub-backend.run.app",
              cloudRunDeviceRegistered = true,
              cloudRunMetaSecretsVerified = true,
              cloudRunWebhookVerified = true,
              cloudRunQueueConfigured = true,
              gcpBudgetAlertConfirmed = true,
            ),
          keyStatus = fakeKeyManager.getOrCreateStatus(),
          senderProfiles =
            listOf(
              SenderProfile(
                id = 1L,
                label = "Main",
                phoneNumberE164 = "+919876543210",
                cloudRunProfileRef = "default",
                isDefault = true,
                createdAt = 1L,
              )
            ),
          completedSimulationsCount = 1,
        )
      assertTrue(complete.all { it.isVerified })

      var capturedHeaders: Map<String, String> = emptyMap()
      val interceptor = Interceptor { chain ->
        val req = chain.request()
        capturedHeaders =
          mapOf(
            CloudRunApiClient.HEADER_KEY_FINGERPRINT to
              (req.header(CloudRunApiClient.HEADER_KEY_FINGERPRINT) ?: ""),
            CloudRunApiClient.HEADER_TIMESTAMP to
              (req.header(CloudRunApiClient.HEADER_TIMESTAMP) ?: ""),
            CloudRunApiClient.HEADER_NONCE to (req.header(CloudRunApiClient.HEADER_NONCE) ?: ""),
            CloudRunApiClient.HEADER_SIGNATURE to
              (req.header(CloudRunApiClient.HEADER_SIGNATURE) ?: ""),
          )
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(
            """{"ok":true,"deviceRegistered":true,"metaSecretsConfigured":true,"webhookVerifyConfigured":true,"cloudTasksConfigured":true}"""
              .toResponseBody("application/json".toMediaType())
          )
          .build()
      }

      val mockClient =
        CloudRunApiClient(
          deviceKeyManager = fakeKeyManager,
          httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )

      // Plain HTTP must be rejected
      assertTrue(mockClient.checkReadiness("http://insecure.example.com").isFailure)

      val readiness = mockClient.checkReadiness("https://reachhub-backend.run.app").getOrThrow()
      assertTrue(readiness.ok)
      assertTrue(capturedHeaders[CloudRunApiClient.HEADER_KEY_FINGERPRINT]?.isNotBlank() == true)
      assertTrue(capturedHeaders[CloudRunApiClient.HEADER_TIMESTAMP]?.isNotBlank() == true)
      assertTrue(capturedHeaders[CloudRunApiClient.HEADER_NONCE]?.isNotBlank() == true)
      assertTrue(capturedHeaders[CloudRunApiClient.HEADER_SIGNATURE]?.isNotBlank() == true)
    }

  private class FakeEcdsaDeviceKeyManager : DeviceKeyManager {
    private var keyPair = generateEcPair()

    private fun generateEcPair() =
      KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
      }

    override suspend fun getOrCreateStatus(): DeviceKeyStatus {
      val spki = keyPair.public.encoded
      val digest = MessageDigest.getInstance("SHA-256").digest(spki)
      val fp = digest.joinToString(":") { "%02X".format(it) }
      val b64 = android.util.Base64.encodeToString(spki, android.util.Base64.NO_WRAP)
      return DeviceKeyStatus(
        isReady = true,
        keyAlias = "test_ecdsa_p256",
        publicKeyFingerprint = fp,
        publicKeyBase64 = b64,
      )
    }

    override suspend fun signPayload(payload: ByteArray): ByteArray =
      Signature.getInstance("SHA256withECDSA").run {
        initSign(keyPair.private)
        update(payload)
        sign()
      }

    override suspend fun rotateDeviceKey(): DeviceKeyStatus {
      keyPair = generateEcPair()
      return getOrCreateStatus()
    }
  }
}
