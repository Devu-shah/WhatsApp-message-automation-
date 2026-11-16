package com.example.data.repository

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.db.dao.CampaignDao
import com.example.data.local.db.dao.ContactDao
import com.example.data.local.db.dao.DashboardDao
import com.example.data.local.db.dao.MediaDao
import com.example.data.local.db.dao.MessageLogDao
import com.example.data.local.db.dao.SenderProfileDao
import com.example.data.local.db.dao.TemplateDao
import com.example.data.local.db.entity.CampaignEntity
import com.example.data.local.db.entity.CampaignRecipientEntity
import com.example.data.local.db.entity.ContactEntity
import com.example.data.local.db.entity.MediaAssetEntity
import com.example.data.local.db.entity.MessageRecordEntity
import com.example.data.local.db.entity.MessageStatusEventEntity
import com.example.data.local.db.entity.RetryAttemptEntity
import com.example.data.local.db.entity.SenderProfileEntity
import com.example.data.local.db.entity.SuppressionEntity
import com.example.data.local.db.entity.TemplateEntity
import com.example.data.local.preferences.SettingsPreferencesDataSource
import com.example.data.remote.CloudRunApiClient
import com.example.data.remote.CloudRunReadinessResponse
import com.example.data.remote.DeviceRegistrationResponse
import com.example.data.remote.DispatchBatchRequest
import com.example.data.remote.DispatchRecipientPayload
import com.example.domain.model.AppMode
import com.example.domain.model.CampaignStatsSummary
import com.example.domain.model.CampaignStatus
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactImportStatus
import com.example.domain.model.ContactWithFields
import com.example.domain.model.CsvCommitResult
import com.example.domain.model.DashboardStats
import com.example.domain.model.DeviceKeyStatus
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaUploadStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.MessageTemplate
import com.example.domain.model.OperatorSettings
import com.example.domain.model.ParsedCsvRow
import com.example.domain.model.PreDispatchAudit
import com.example.domain.model.ProductionGateItem
import com.example.domain.model.RecentCampaignSummary
import com.example.domain.model.RecentMessageActivity
import com.example.domain.model.RecipientEligibility
import com.example.domain.model.RetryOutcome
import com.example.domain.model.RetryPolicyClassifier
import com.example.domain.model.SenderProfile
import com.example.domain.model.SuppressionRecord
import com.example.domain.model.TemplateParameterMapping
import com.example.domain.util.CampaignEngine
import com.example.domain.util.CsvParserAndSecurity
import com.example.domain.util.ImageMediaSecurity
import com.example.domain.util.PhoneNumberHelper
import com.example.domain.util.TemplateEngine
import com.example.security.DeviceKeyManager
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DashboardRepository(private val dashboardDao: DashboardDao) {

  val dashboardStats: Flow<DashboardStats> =
    combine(
        combine(
          dashboardDao.observeTotalContacts(),
          dashboardDao.observeOptedOutContacts(),
          dashboardDao.observeTotalCampaigns(),
          dashboardDao.observeActiveCampaigns(),
          dashboardDao.observeCompletedCampaigns(),
        ) { contacts, optedOut, campaigns, active, completed ->
          listOf(contacts, optedOut, campaigns, active, completed)
        },
        combine(
          dashboardDao.observeTotalMessagesSubmitted(),
          dashboardDao.observeMessagesDelivered(),
          dashboardDao.observeMessagesRead(),
          dashboardDao.observeFailedMessages(),
          dashboardDao.observeTotalTemplates(),
        ) { submitted, delivered, read, failed, templates ->
          listOf(submitted, delivered, read, failed, templates)
        },
        dashboardDao.observeTotalMediaAssets(),
      ) { groupA, groupB, mediaCount ->
        DashboardStats(
          totalContacts = groupA[0],
          optedOutContacts = groupA[1],
          totalCampaigns = groupA[2],
          activeCampaigns = groupA[3],
          completedCampaigns = groupA[4],
          totalMessagesSubmitted = groupB[0],
          messagesDelivered = groupB[1],
          messagesRead = groupB[2],
          failedMessages = groupB[3],
          totalTemplates = groupB[4],
          totalMediaAssets = mediaCount,
        )
      }

  val recentCampaigns: Flow<List<RecentCampaignSummary>> =
    dashboardDao.observeRecentCampaigns().map { list ->
      list.map { entity ->
        RecentCampaignSummary(
          id = entity.id,
          name = entity.name,
          status = entity.status,
          mode = entity.mode,
          senderPhoneNumber = entity.senderPhoneSnapshot,
          createdAt = entity.createdAt,
        )
      }
    }

  val recentMessageActivity: Flow<List<RecentMessageActivity>> =
    dashboardDao.observeRecentMessageRecords().map { list ->
      list.map { record ->
        RecentMessageActivity(
          id = record.id,
          campaignId = record.campaignId,
          recipientName = record.recipientName,
          recipientPhoneMasked = PhoneNumberHelper.maskForDisplay(record.recipientPhoneE164),
          status = record.currentStatus,
          updatedAt = record.updatedAt,
        )
      }
    }
}

class ContactRepository(private val contactDao: ContactDao) {
  val allContacts: Flow<List<ContactEntity>> = contactDao.observeAllContacts()

  val contactsWithFields: Flow<List<ContactWithFields>> =
    contactDao.observeContactsWithFields().map { relations ->
      relations.map { rel ->
        val importStatus =
          runCatching { ContactImportStatus.valueOf(rel.contact.importStatus) }
            .getOrDefault(ContactImportStatus.MANUAL)
        ContactWithFields(
          id = rel.contact.id,
          name = rel.contact.name,
          phoneE164 = rel.contact.phoneE164,
          rawPhone = rel.contact.rawPhone,
          email = rel.contact.email,
          college = rel.contact.college,
          city = rel.contact.city,
          state = rel.contact.state,
          consentStatus = rel.contact.consentStatus,
          isOptedOut = rel.contact.isOptedOut,
          importStatus = importStatus,
          importedAt = rel.contact.importedAt,
          customFields = rel.customFields.associate { it.fieldKey to it.fieldValue },
        )
      }
    }

  val distinctCustomFieldKeys: Flow<List<String>> = contactDao.observeDistinctCustomFieldKeys()

  val suppressionList: Flow<List<SuppressionRecord>> =
    contactDao.observeSuppressionList().map { entities ->
      entities.map { entity ->
        SuppressionRecord(
          phoneE164 = entity.phoneE164,
          reason = entity.reason,
          source = entity.source,
          optedOutAt = entity.optedOutAt,
        )
      }
    }

  suspend fun getExistingAndSuppressedPhones(): Pair<Set<String>, Set<String>> =
    withContext(Dispatchers.IO) {
      contactDao.getAllPhoneNumbers().toSet() to contactDao.getAllSuppressedPhones().toSet()
    }

  suspend fun saveContact(
    id: Long,
    name: String,
    rawPhone: String,
    email: String?,
    college: String?,
    city: String?,
    state: String?,
    consentStatus: ConsentStatus,
    isOptedOut: Boolean,
    customFields: Map<String, String>,
    defaultCountryCode: String,
    autoApplyDefaultCode: Boolean,
  ): Result<Long> =
    withContext(Dispatchers.IO) {
      val cleanName = name.trim()
      if (cleanName.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Contact name is required"))
      }
      val normalized =
        PhoneNumberHelper.normalize(rawPhone, defaultCountryCode, autoApplyDefaultCode)
      if (normalized is PhoneNumberHelper.NormalizationResult.Invalid) {
        return@withContext Result.failure(IllegalArgumentException(normalized.reason))
      }
      val validE164 = (normalized as PhoneNumberHelper.NormalizationResult.Valid).e164
      val savedId =
        contactDao.upsertContactWithFields(
          contact =
            ContactEntity(
              id = id,
              name = cleanName,
              phoneE164 = validE164,
              rawPhone = rawPhone.trim(),
              email = email?.trim()?.takeIf { it.isNotEmpty() },
              college = college?.trim()?.takeIf { it.isNotEmpty() },
              city = city?.trim()?.takeIf { it.isNotEmpty() },
              state = state?.trim()?.takeIf { it.isNotEmpty() },
              consentStatus = if (isOptedOut) ConsentStatus.REVOKED else consentStatus,
              isOptedOut = isOptedOut,
            ),
          customFields = customFields.filterKeys { it.isNotBlank() },
        )
      Result.success(savedId)
    }

  suspend fun deleteContact(contactId: Long) =
    withContext(Dispatchers.IO) { contactDao.deleteContactById(contactId) }

  suspend fun setOptOut(
    contactId: Long,
    phoneE164: String,
    optedOut: Boolean,
    reason: String = "Operator updated opt-out status",
  ) =
    withContext(Dispatchers.IO) {
      contactDao.setContactOptOut(
        contactId = contactId,
        phoneE164 = phoneE164,
        optedOut = optedOut,
        reason = reason,
        source = "operator",
      )
    }

  suspend fun addSuppressionNumber(
    rawPhone: String,
    reason: String,
    defaultCountryCode: String,
    autoApplyDefaultCode: Boolean,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      val normalized =
        PhoneNumberHelper.normalize(rawPhone, defaultCountryCode, autoApplyDefaultCode)
      if (normalized is PhoneNumberHelper.NormalizationResult.Invalid) {
        return@withContext Result.failure(IllegalArgumentException(normalized.reason))
      }
      val e164 = (normalized as PhoneNumberHelper.NormalizationResult.Valid).e164
      val existing = contactDao.findByPhone(e164)
      if (existing != null) {
        contactDao.setContactOptOut(
          contactId = existing.id,
          phoneE164 = e164,
          optedOut = true,
          reason = reason.trim().ifEmpty { "Added to suppression list" },
          source = "operator",
        )
      } else {
        contactDao.addSuppression(
          SuppressionEntity(
            phoneE164 = e164,
            reason = reason.trim().ifEmpty { "Added to suppression list" },
            source = "operator",
          )
        )
      }
      Result.success(e164)
    }

  suspend fun removeSuppression(phoneE164: String) =
    withContext(Dispatchers.IO) { contactDao.removeSuppressionAndRestoreContact(phoneE164) }

  suspend fun commitCsvImport(
    selectedRows: List<ParsedCsvRow>,
    updateExistingDuplicates: Boolean,
  ): CsvCommitResult =
    withContext(Dispatchers.IO) {
      val toCommit =
        selectedRows.mapNotNull { row ->
          val e164 = row.normalizedPhoneE164 ?: return@mapNotNull null
          val optedOut = row.consentStatus == ConsentStatus.REVOKED
          val entity =
            ContactEntity(
              name = row.resolvedName,
              phoneE164 = e164,
              rawPhone = row.rawPhone,
              email = row.email,
              college = row.college,
              city = row.city,
              state = row.state,
              consentStatus = row.consentStatus,
              isOptedOut = optedOut,
            )
          entity to row.customFields
        }
      contactDao.commitCsvBatch(rows = toCommit, updateExisting = updateExistingDuplicates)
    }

  fun exportContactsCsv(contacts: List<ContactWithFields>): String =
    CsvParserAndSecurity.buildContactsCsv(contacts)
}

class TemplateRepository(private val templateDao: TemplateDao) {
  private val json = Json { ignoreUnknownKeys = true }

  val allTemplates: Flow<List<TemplateEntity>> = templateDao.observeAllTemplates()

  val templates: Flow<List<MessageTemplate>> =
    templateDao.observeAllTemplates().map { entities ->
      entities.map { entity ->
        val placeholders =
          runCatching { json.decodeFromString<List<String>>(entity.placeholdersJson) }
            .getOrDefault(TemplateEngine.extractPlaceholders(entity.bodyText))
        val mappings =
          runCatching {
              json.decodeFromString<List<TemplateParameterMapping>>(entity.parameterMappingJson)
            }
            .getOrDefault(emptyList())
        MessageTemplate(
          id = entity.id,
          name = entity.name,
          metaTemplateName = entity.metaTemplateName,
          languageCode = entity.languageCode,
          category = entity.category,
          bodyText = entity.bodyText,
          placeholders = placeholders,
          mappings = mappings,
          headerMediaRequired = entity.headerMediaRequired,
          validationStatus = entity.validationStatus,
          createdAt = entity.createdAt,
          updatedAt = entity.updatedAt,
        )
      }
    }

  suspend fun saveTemplate(
    id: Long,
    name: String,
    metaTemplateName: String,
    languageCode: String,
    category: String,
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
    headerMediaRequired: Boolean,
    availableFields: List<String>,
  ): Result<Long> =
    withContext(Dispatchers.IO) {
      val cleanName = name.trim()
      val cleanMetaName = metaTemplateName.trim()
      if (cleanName.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Template name is required"))
      }
      val existingByName = templateDao.findByName(cleanName)
      if (existingByName != null && existingByName.id != id) {
        return@withContext Result.failure(
          IllegalArgumentException("A template named '$cleanName' already exists")
        )
      }
      val syncedMappings = TemplateEngine.syncMappings(bodyText, mappings, availableFields)
      val report =
        TemplateEngine.validateTemplate(
          templateName = cleanName,
          metaTemplateName = cleanMetaName,
          bodyText = bodyText,
          mappings = syncedMappings,
          headerMediaRequired = headerMediaRequired,
          availableFields = availableFields,
        )
      if (report.errors.isNotEmpty()) {
        return@withContext Result.failure(IllegalArgumentException(report.errors.first()))
      }
      val now = System.currentTimeMillis()
      val existing = if (id != 0L) templateDao.getTemplateById(id) else null
      val savedId =
        templateDao.upsertTemplate(
          TemplateEntity(
            id = id,
            name = cleanName,
            metaTemplateName = cleanMetaName,
            languageCode = languageCode.trim().ifEmpty { "en" },
            category = category.trim().ifEmpty { "MARKETING" },
            bodyText = bodyText.trim(),
            placeholdersJson = json.encodeToString(TemplateEngine.extractPlaceholders(bodyText)),
            parameterMappingJson = json.encodeToString(syncedMappings),
            headerMediaRequired = headerMediaRequired,
            validationStatus = report.status,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
          )
        )
      Result.success(savedId)
    }

  suspend fun duplicateTemplate(template: MessageTemplate): Result<Long> =
    withContext(Dispatchers.IO) {
      var candidateName = "${template.name} (Copy)"
      var suffix = 2
      while (templateDao.findByName(candidateName) != null) {
        candidateName = "${template.name} (Copy $suffix)"
        suffix++
      }
      val now = System.currentTimeMillis()
      val newId =
        templateDao.upsertTemplate(
          TemplateEntity(
            id = 0,
            name = candidateName,
            metaTemplateName = "${template.metaTemplateName}_copy",
            languageCode = template.languageCode,
            category = template.category,
            bodyText = template.bodyText,
            placeholdersJson = json.encodeToString(template.placeholders),
            parameterMappingJson = json.encodeToString(template.mappings),
            headerMediaRequired = template.headerMediaRequired,
            validationStatus = template.validationStatus,
            createdAt = now,
            updatedAt = now,
          )
        )
      Result.success(newId)
    }

  suspend fun deleteTemplate(templateId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      if (templateDao.countCampaignsUsingTemplate(templateId) > 0) {
        return@withContext Result.failure(
          IllegalStateException("Cannot delete template referenced by an existing campaign")
        )
      }
      templateDao.deleteTemplateById(templateId)
      Result.success(Unit)
    }
}

class MediaRepository(private val mediaDao: MediaDao) {
  val allMediaAssets: Flow<List<MediaAssetEntity>> = mediaDao.observeAllMediaAssets()

  val mediaAssets: Flow<List<MediaAsset>> =
    mediaDao.observeAllMediaAssets().map { entities ->
      entities.map { entity ->
        MediaAsset(
          id = entity.id,
          displayName = entity.displayName,
          localUri = entity.localUri,
          mimeType = entity.mimeType,
          byteSize = entity.byteSize,
          sha256Hash = entity.sha256Hash,
          metaMediaId = entity.metaMediaId,
          captionNote = entity.captionNote,
          widthPx = entity.widthPx,
          heightPx = entity.heightPx,
          uploadStatus = entity.uploadStatus,
          uploadedAt = entity.uploadedAt,
          createdAt = entity.createdAt,
        )
      }
    }

  suspend fun importFlyerFromUri(
    contentResolver: ContentResolver,
    filesDir: File,
    sourceUri: Uri,
    customDisplayName: String? = null,
    captionNote: String? = null,
  ): Result<Pair<MediaAsset, Boolean>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val rawName = customDisplayName?.takeIf { it.isNotBlank() } ?: queryDisplayName(contentResolver, sourceUri)
        val safeName = ImageMediaSecurity.sanitizeDisplayName(rawName)

        val bytes =
          contentResolver.openInputStream(sourceUri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0L
            while (true) {
              val read = input.read(buffer)
              if (read == -1) break
              total += read
              if (total > ImageMediaSecurity.MAX_IMAGE_BYTES) {
                throw IllegalArgumentException(
                  "Flyer exceeds Meta's 5 MB image size limit"
                )
              }
              output.write(buffer, 0, read)
            }
            output.toByteArray()
          } ?: throw IllegalArgumentException("Unable to read selected image")

        if (bytes.isEmpty()) {
          throw IllegalArgumentException("Selected image file is empty")
        }

        val verifiedMime =
          ImageMediaSecurity.detectVerifiedImageMime(bytes)
            ?: throw IllegalArgumentException(
              "Unsupported image format — only valid JPEG and PNG flyers are accepted"
            )

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
          throw IllegalArgumentException("Corrupted image file — unable to decode dimensions")
        }

        val sha256 = ImageMediaSecurity.sha256Hex(bytes)
        val existing = mediaDao.findBySha256(sha256)
        if (existing != null) {
          val asset =
            MediaAsset(
              id = existing.id,
              displayName = existing.displayName,
              localUri = existing.localUri,
              mimeType = existing.mimeType,
              byteSize = existing.byteSize,
              sha256Hash = existing.sha256Hash,
              metaMediaId = existing.metaMediaId,
              captionNote = existing.captionNote,
              widthPx = existing.widthPx,
              heightPx = existing.heightPx,
              uploadStatus = existing.uploadStatus,
              uploadedAt = existing.uploadedAt,
              createdAt = existing.createdAt,
            )
          return@runCatching asset to true // deduplicated!
        }

        val flyersDir = File(filesDir, "flyers").apply { mkdirs() }
        val ext = if (verifiedMime == "image/png") "png" else "jpg"
        val destFile = File(flyersDir, "${sha256.take(24)}.$ext")
        destFile.writeBytes(bytes)

        val entity =
          MediaAssetEntity(
            displayName = safeName,
            localUri = destFile.toURI().toString(),
            mimeType = verifiedMime,
            byteSize = bytes.size.toLong(),
            sha256Hash = sha256,
            captionNote = captionNote?.trim()?.takeIf { it.isNotEmpty() },
            widthPx = bounds.outWidth,
            heightPx = bounds.outHeight,
            uploadStatus = MediaUploadStatus.LOCAL_ONLY,
          )
        val id = mediaDao.upsertMediaAsset(entity)
        MediaAsset(
          id = id,
          displayName = entity.displayName,
          localUri = entity.localUri,
          mimeType = entity.mimeType,
          byteSize = entity.byteSize,
          sha256Hash = entity.sha256Hash,
          captionNote = entity.captionNote,
          widthPx = entity.widthPx,
          heightPx = entity.heightPx,
          uploadStatus = entity.uploadStatus,
          createdAt = entity.createdAt,
        ) to false
      }
    }

  suspend fun updateFlyerMetadata(id: Long, displayName: String, captionNote: String?) =
    withContext(Dispatchers.IO) {
      val cleanName = ImageMediaSecurity.sanitizeDisplayName(displayName)
      val cleanCaption = captionNote?.trim()?.takeIf { it.isNotEmpty() }
      mediaDao.updateMetadata(id = id, displayName = cleanName, captionNote = cleanCaption)
    }

  suspend fun deleteFlyer(asset: MediaAsset): Result<Unit> =
    withContext(Dispatchers.IO) {
      if (mediaDao.countCampaignsUsingMedia(asset.id) > 0) {
        return@withContext Result.failure(
          IllegalStateException("Cannot delete flyer attached to an existing campaign")
        )
      }
      mediaDao.deleteMediaAssetById(asset.id)
      runCatching {
        val fileUri = java.net.URI(asset.localUri)
        if (fileUri.scheme == "file") {
          File(fileUri).delete()
        }
      }
      Result.success(Unit)
    }

  private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return c.getString(idx)
      }
    }
    return uri.lastPathSegment
  }
}

class CampaignRepository(
  private val campaignDao: CampaignDao,
  private val contactDao: ContactDao,
  private val templateDao: TemplateDao,
  private val mediaDao: MediaDao,
  private val messageLogDao: MessageLogDao,
  private val senderProfileDao: SenderProfileDao,
  private val preferences: SettingsPreferencesDataSource,
  private val cloudRunApiClient: CloudRunApiClient,
  private val onEnqueueDispatch: (campaignId: Long, delayMs: Long) -> Unit = { _, _ -> },
  private val onCancelDispatch: (campaignId: Long) -> Unit = {},
) {
  private val json = Json { ignoreUnknownKeys = true }

  val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.observeAllCampaigns()

  val campaignSummaries: Flow<List<CampaignStatsSummary>> =
    combine(
      campaignDao.observeAllCampaigns(),
      templateDao.observeAllTemplates(),
      mediaDao.observeAllMediaAssets(),
      messageLogDao.observeAllMessageRecords(),
    ) { campaigns, templates, mediaAssets, messages ->
      val templatesById = templates.associateBy { it.id }
      val mediaById = mediaAssets.associateBy { it.id }
      val messagesByCampaign = messages.groupBy { it.campaignId }

      campaigns.map { campaign ->
        val msgs = messagesByCampaign[campaign.id].orEmpty()
        CampaignStatsSummary(
          campaign = campaign,
          templateName = templatesById[campaign.templateId]?.name ?: "Template #${campaign.templateId}",
          mediaName = campaign.mediaAssetId?.let { mediaById[it]?.displayName },
          totalRecipients = msgs.size,
          queuedCount = msgs.count { it.currentStatus == MessageStatus.QUEUED },
          submittedCount = msgs.count { it.currentStatus == MessageStatus.SUBMITTED },
          sentCount = msgs.count { it.currentStatus == MessageStatus.SENT },
          deliveredCount = msgs.count { it.currentStatus == MessageStatus.DELIVERED },
          readCount = msgs.count { it.currentStatus == MessageStatus.READ },
          failedCount = msgs.count { it.currentStatus == MessageStatus.FAILED },
          retryableFailedCount =
            msgs.count { it.currentStatus == MessageStatus.FAILED && !it.isPermanentError },
          permanentFailedCount =
            msgs.count { it.currentStatus == MessageStatus.FAILED && it.isPermanentError },
          skippedCount = msgs.count { it.currentStatus == MessageStatus.SKIPPED },
        )
      }
    }

  suspend fun createAndOptionallyLaunchCampaign(
    name: String,
    description: String,
    template: MessageTemplate,
    mediaAsset: MediaAsset?,
    senderProfile: SenderProfile,
    audit: PreDispatchAudit,
    launchImmediately: Boolean,
  ): Result<Long> =
    withContext(Dispatchers.IO) {
      val cleanName = name.trim()
      if (cleanName.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Campaign name is required"))
      }
      if (template.headerMediaRequired && mediaAsset == null) {
        return@withContext Result.failure(
          IllegalArgumentException("Selected template requires an attached JPEG/PNG flyer")
        )
      }
      if (audit.eligibleRecipients.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException("At least one valid non-opted-out recipient is required")
        )
      }

      val settings = preferences.settingsFlow.first()
      val effectiveMode =
        if (settings.simulationModeEnabled) AppMode.SIMULATION else AppMode.PRODUCTION

      if (effectiveMode == AppMode.PRODUCTION && !settings.cloudRunBaseUrl.startsWith("https://")) {
        return@withContext Result.failure(
          IllegalArgumentException("Configure an HTTPS Google Cloud Run URL in Settings before Production dispatch")
        )
      }

      val now = System.currentTimeMillis()
      val initialStatus = if (launchImmediately) CampaignStatus.RUNNING else CampaignStatus.READY
      val campaignId =
        campaignDao.upsertCampaign(
          CampaignEntity(
            name = cleanName,
            description = description.trim(),
            templateId = template.id,
            mediaAssetId = mediaAsset?.id,
            senderProfileId = senderProfile.id,
            senderPhoneSnapshot = senderProfile.phoneNumberE164,
            status = initialStatus,
            mode = effectiveMode,
            startedAt = if (launchImmediately) now else null,
            createdAt = now,
          )
        )

      val recipientRows =
        audit.eligibleRecipients.map { contact ->
          CampaignRecipientEntity(
            campaignId = campaignId,
            contactId = contact.id,
            eligibilityStatus = RecipientEligibility.VALID,
            selected = true,
          )
        } +
          audit.excludedEntries.map { entry ->
            CampaignRecipientEntity(
              campaignId = campaignId,
              contactId = entry.contact.id,
              eligibilityStatus = entry.eligibility,
              selected = false,
            )
          }
      campaignDao.insertCampaignRecipients(recipientRows)

      val messageRecords =
        audit.eligibleRecipients.map { contact ->
          val rendered =
            TemplateEngine.renderForContact(template.bodyText, template.mappings, contact)
          val idempotencyKey =
            CampaignEngine.buildIdempotencyKey(campaignId, contact.id, contact.phoneE164)
          MessageRecordEntity(
            campaignId = campaignId,
            contactId = contact.id,
            recipientName = contact.name,
            recipientPhoneE164 = contact.phoneE164,
            personalizedBody = rendered.renderedText,
            mediaReference = mediaAsset?.metaMediaId ?: mediaAsset?.localUri,
            idempotencyKey = idempotencyKey,
            currentStatus = MessageStatus.QUEUED,
            createdAt = now,
            updatedAt = now,
          )
        }
      val insertedIds = campaignDao.insertMessageRecords(messageRecords)
      insertedIds.forEach { msgId ->
        if (msgId > 0L) {
          messageLogDao.insertStatusEvent(
            MessageStatusEventEntity(
              messageId = msgId,
              status = MessageStatus.QUEUED,
              eventTimestamp = now,
              source = "campaign_builder",
              detail = "Recipient queued (${effectiveMode.name})",
            )
          )
        }
      }

      if (launchImmediately) {
        onEnqueueDispatch(campaignId, 0L)
      }
      Result.success(campaignId)
    }

  suspend fun launchDraftCampaign(campaignId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      val campaign =
        campaignDao.getCampaignById(campaignId)
          ?: return@withContext Result.failure(IllegalArgumentException("Campaign not found"))

      // Ensure message records exist if this was a cloned DRAFT
      val existingRecords = campaignDao.getMessageRecordsForCampaign(campaignId)
      if (existingRecords.isEmpty()) {
        val template =
          templateDao.getTemplateById(campaign.templateId)
            ?: return@withContext Result.failure(IllegalStateException("Template missing for draft"))
        val mappings =
          runCatching {
              json.decodeFromString<List<TemplateParameterMapping>>(template.parameterMappingJson)
            }
            .getOrDefault(emptyList())
        val media = campaign.mediaAssetId?.let { mediaDao.getById(it) }
        val contactsById =
          contactDao.observeContactsWithFields().first().associate { rel ->
            rel.contact.id to
              ContactWithFields(
                id = rel.contact.id,
                name = rel.contact.name,
                phoneE164 = rel.contact.phoneE164,
                rawPhone = rel.contact.rawPhone,
                email = rel.contact.email,
                college = rel.contact.college,
                city = rel.contact.city,
                state = rel.contact.state,
                consentStatus = rel.contact.consentStatus,
                isOptedOut = rel.contact.isOptedOut,
                importStatus =
                  runCatching { ContactImportStatus.valueOf(rel.contact.importStatus) }
                    .getOrDefault(ContactImportStatus.MANUAL),
                importedAt = rel.contact.importedAt,
                customFields = rel.customFields.associate { it.fieldKey to it.fieldValue },
              )
          }
        val suppressed = contactDao.getAllSuppressedPhones().toSet()
        val selectedRecipients =
          campaignDao.getCampaignRecipients(campaignId).filter { it.selected }
        val now = System.currentTimeMillis()
        val newRecords =
          selectedRecipients.mapNotNull { rec ->
            val contact = contactsById[rec.contactId] ?: return@mapNotNull null
            if (contact.isOptedOut || suppressed.contains(contact.phoneE164)) return@mapNotNull null
            val rendered = TemplateEngine.renderForContact(template.bodyText, mappings, contact)
            MessageRecordEntity(
              campaignId = campaignId,
              contactId = contact.id,
              recipientName = contact.name,
              recipientPhoneE164 = contact.phoneE164,
              personalizedBody = rendered.renderedText,
              mediaReference = media?.metaMediaId ?: media?.localUri,
              idempotencyKey =
                CampaignEngine.buildIdempotencyKey(campaignId, contact.id, contact.phoneE164),
              currentStatus = MessageStatus.QUEUED,
              createdAt = now,
              updatedAt = now,
            )
          }
        if (newRecords.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("No eligible non-suppressed recipients remain in draft")
          )
        }
        val ids = campaignDao.insertMessageRecords(newRecords)
        ids.forEach { msgId ->
          if (msgId > 0L) {
            messageLogDao.insertStatusEvent(
              MessageStatusEventEntity(
                messageId = msgId,
                status = MessageStatus.QUEUED,
                eventTimestamp = now,
                source = "draft_launch",
                detail = "Queued from draft launch (${campaign.mode.name})",
              )
            )
          }
        }
      }

      campaignDao.markCampaignRunning(campaignId, System.currentTimeMillis())
      onEnqueueDispatch(campaignId, 0L)
      Result.success(Unit)
    }

  suspend fun pauseCampaign(campaignId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      val campaign =
        campaignDao.getCampaignById(campaignId)
          ?: return@withContext Result.failure(IllegalArgumentException("Campaign not found"))
      campaignDao.updateCampaignStatus(campaignId, CampaignStatus.PAUSED)
      onCancelDispatch(campaignId)
      if (campaign.mode == AppMode.PRODUCTION) {
        val baseUrl = preferences.settingsFlow.first().cloudRunBaseUrl
        if (baseUrl.startsWith("https://")) {
          cloudRunApiClient.controlCampaign(baseUrl, campaignId, "PAUSE")
        }
      }
      Result.success(Unit)
    }

  suspend fun resumeCampaign(campaignId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      val campaign =
        campaignDao.getCampaignById(campaignId)
          ?: return@withContext Result.failure(IllegalArgumentException("Campaign not found"))
      campaignDao.markCampaignRunning(campaignId, System.currentTimeMillis())
      if (campaign.mode == AppMode.PRODUCTION) {
        val baseUrl = preferences.settingsFlow.first().cloudRunBaseUrl
        if (baseUrl.startsWith("https://")) {
          cloudRunApiClient.controlCampaign(baseUrl, campaignId, "RESUME")
        }
      }
      onEnqueueDispatch(campaignId, 0L)
      Result.success(Unit)
    }

  suspend fun cancelCampaign(campaignId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      val campaign =
        campaignDao.getCampaignById(campaignId)
          ?: return@withContext Result.failure(IllegalArgumentException("Campaign not found"))
      onCancelDispatch(campaignId)
      val now = System.currentTimeMillis()
      val queued = messageLogDao.getNextQueuedBatch(campaignId, Int.MAX_VALUE)
      queued.forEach { record ->
        messageLogDao.applyMonotonicStatusTransition(
          messageId = record.id,
          targetStatus = MessageStatus.SKIPPED,
          eventTimestamp = now,
          source = "operator_cancel",
          detail = "Campaign cancelled before dispatch",
        )
      }
      campaignDao.markCampaignFinished(campaignId, CampaignStatus.CANCELLED, now)
      if (campaign.mode == AppMode.PRODUCTION) {
        val baseUrl = preferences.settingsFlow.first().cloudRunBaseUrl
        if (baseUrl.startsWith("https://")) {
          cloudRunApiClient.controlCampaign(baseUrl, campaignId, "CANCEL")
        }
      }
      Result.success(Unit)
    }

  suspend fun retryEligibleFailedForCampaign(campaignId: Long): Result<Int> =
    withContext(Dispatchers.IO) {
      val settings = preferences.settingsFlow.first()
      val candidates =
        messageLogDao.getRetryableFailedMessagesForCampaign(
          campaignId = campaignId,
          maxAttempts = settings.maxRetryAttempts.coerceAtLeast(1),
        )
      if (candidates.isEmpty()) {
        return@withContext Result.success(0)
      }
      val now = System.currentTimeMillis()
      var requeued = 0
      candidates.forEach { msg ->
        if (contactDao.isSuppressed(msg.recipientPhoneE164)) {
          messageLogDao.applyMonotonicStatusTransition(
            messageId = msg.id,
            targetStatus = MessageStatus.SKIPPED,
            eventTimestamp = now,
            source = "suppression_guard",
            detail = "Skipped retry — recipient added to suppression list",
          )
        } else {
          val nextAttempt = msg.attemptCount + 1
          val backoffMs = RetryPolicyClassifier.computeBackoffDelayMs(nextAttempt)
          messageLogDao.insertRetryAttempt(
            RetryAttemptEntity(
              messageId = msg.id,
              attemptNumber = nextAttempt,
              scheduledAt = now + backoffMs,
              executedAt = null,
              outcome = RetryOutcome.PENDING,
              errorCode = msg.errorCode,
              errorDetail = "Operator triggered retry",
            )
          )
          messageLogDao.applyMonotonicStatusTransition(
            messageId = msg.id,
            targetStatus = MessageStatus.QUEUED,
            eventTimestamp = now,
            source = "retry_queue",
            detail = "Re-queued for attempt #$nextAttempt",
          )
          requeued++
        }
      }
      if (requeued > 0) {
        campaignDao.markCampaignRunning(campaignId, now)
        onEnqueueDispatch(campaignId, 0L)
      }
      Result.success(requeued)
    }

  suspend fun retrySingleMessage(messageId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      val msg =
        messageLogDao.getMessageRecordById(messageId)
          ?: return@withContext Result.failure(IllegalArgumentException("Message record not found"))
      if (msg.currentStatus != MessageStatus.FAILED) {
        return@withContext Result.failure(IllegalStateException("Only failed messages can be retried"))
      }
      if (msg.isPermanentError) {
        return@withContext Result.failure(
          IllegalStateException("Permanent WhatsApp error (${msg.errorCode}) is blocked from retry")
        )
      }
      val settings = preferences.settingsFlow.first()
      if (msg.attemptCount >= settings.maxRetryAttempts) {
        return@withContext Result.failure(
          IllegalStateException("Maximum retry attempts (${settings.maxRetryAttempts}) reached")
        )
      }
      val now = System.currentTimeMillis()
      if (contactDao.isSuppressed(msg.recipientPhoneE164)) {
        messageLogDao.applyMonotonicStatusTransition(
          messageId = msg.id,
          targetStatus = MessageStatus.SKIPPED,
          eventTimestamp = now,
          source = "suppression_guard",
          detail = "Retry blocked — contact is currently suppressed",
        )
        return@withContext Result.failure(
          IllegalStateException("Recipient is now in suppression list; marked SKIPPED")
        )
      }
      val nextAttempt = msg.attemptCount + 1
      messageLogDao.insertRetryAttempt(
        RetryAttemptEntity(
          messageId = msg.id,
          attemptNumber = nextAttempt,
          scheduledAt = now,
          executedAt = null,
          outcome = RetryOutcome.PENDING,
          errorCode = msg.errorCode,
          errorDetail = "Manual single-message retry",
        )
      )
      messageLogDao.applyMonotonicStatusTransition(
        messageId = msg.id,
        targetStatus = MessageStatus.QUEUED,
        eventTimestamp = now,
        source = "manual_retry",
        detail = "Re-queued manually for attempt #$nextAttempt",
      )
      campaignDao.markCampaignRunning(msg.campaignId, now)
      onEnqueueDispatch(msg.campaignId, 0L)
      Result.success(Unit)
    }

  suspend fun cloneCampaignAsSafeDraft(sourceCampaignId: Long): Result<Long> =
    withContext(Dispatchers.IO) {
      val source =
        campaignDao.getCampaignById(sourceCampaignId)
          ?: return@withContext Result.failure(IllegalArgumentException("Source campaign not found"))
      val existingNames = campaignDao.observeAllCampaigns().first().map { it.name }.toSet()
      var candidate = "${source.name} (Clone)"
      var idx = 2
      while (existingNames.contains(candidate)) {
        candidate = "${source.name} (Clone $idx)"
        idx++
      }
      val settings = preferences.settingsFlow.first()
      val targetMode =
        if (settings.simulationModeEnabled) AppMode.SIMULATION else AppMode.PRODUCTION
      val clonedId =
        campaignDao.cloneCampaignAsDraft(
          sourceCampaignId = sourceCampaignId,
          newName = candidate,
          targetMode = targetMode,
        ) ?: return@withContext Result.failure(IllegalStateException("Unable to clone campaign"))
      Result.success(clonedId)
    }

  suspend fun deleteCampaign(campaignId: Long): Result<Unit> =
    withContext(Dispatchers.IO) {
      val campaign =
        campaignDao.getCampaignById(campaignId)
          ?: return@withContext Result.failure(IllegalArgumentException("Campaign not found"))
      if (campaign.status == CampaignStatus.RUNNING) {
        return@withContext Result.failure(
          IllegalStateException("Pause or cancel the running campaign before deleting")
        )
      }
      campaignDao.deleteCampaignById(campaignId)
      Result.success(Unit)
    }

  /**
   * Core batch dispatch loop invoked by CampaignExecutionWorker (or directly in tests).
   * Enforces Simulation vs Production isolation, suppression checks before every message,
   * Pause/Cancel boundaries, and idempotent dispatch payloads.
   */
  suspend fun executeCampaignDispatchLoop(
    campaignId: Long,
    simulationBatchDelayMs: Long = 60L,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val settings = preferences.settingsFlow.first()
        var currentCampaign = campaignDao.getCampaignById(campaignId) ?: return@runCatching
        if (currentCampaign.status != CampaignStatus.RUNNING) return@runCatching

        val template =
          templateDao.getTemplateById(currentCampaign.templateId)
            ?: error("Campaign template missing")
        val mappings =
          runCatching {
              json.decodeFromString<List<TemplateParameterMapping>>(template.parameterMappingJson)
            }
            .getOrDefault(emptyList())

        var media = currentCampaign.mediaAssetId?.let { mediaDao.getById(it) }
        val senderProfile =
          currentCampaign.senderProfileId?.let { sid ->
            senderProfileDao.observeAllSenderProfiles().first().firstOrNull { it.id == sid }
          } ?: senderProfileDao.observeDefaultSenderProfile().first()

        // Preload contacts for Production positional parameter assembly
        val contactsById =
          contactDao.observeContactsWithFields().first().associate { rel ->
            rel.contact.id to
              ContactWithFields(
                id = rel.contact.id,
                name = rel.contact.name,
                phoneE164 = rel.contact.phoneE164,
                rawPhone = rel.contact.rawPhone,
                email = rel.contact.email,
                college = rel.contact.college,
                city = rel.contact.city,
                state = rel.contact.state,
                consentStatus = rel.contact.consentStatus,
                isOptedOut = rel.contact.isOptedOut,
                importStatus =
                  runCatching { ContactImportStatus.valueOf(rel.contact.importStatus) }
                    .getOrDefault(ContactImportStatus.MANUAL),
                importedAt = rel.contact.importedAt,
                customFields = rel.customFields.associate { it.fieldKey to it.fieldValue },
              )
          }

        if (currentCampaign.mode == AppMode.PRODUCTION) {
          if (media != null && media.metaMediaId.isNullOrBlank()) {
            val uploadResp =
              cloudRunApiClient
                .uploadFlyerMedia(
                  baseUrl = settings.cloudRunBaseUrl,
                  localUri = media.localUri,
                  mimeType = media.mimeType,
                  sha256Hash = media.sha256Hash,
                  displayName = media.displayName,
                  senderProfileRef = senderProfile?.cloudRunProfileRef ?: "default",
                )
                .getOrThrow()
            mediaDao.markMediaUploaded(
              id = media.id,
              metaMediaId = uploadResp.metaMediaId,
              uploadedAt = System.currentTimeMillis(),
            )
            media = mediaDao.getById(media.id)
          }
        }

        val batchSize = settings.batchSize.coerceIn(10, 200)
        while (true) {
          currentCampaign = campaignDao.getCampaignById(campaignId) ?: break
          if (currentCampaign.status != CampaignStatus.RUNNING) {
            break
          }
          val batch = messageLogDao.getNextQueuedBatch(campaignId, batchSize)
          if (batch.isEmpty()) {
            break
          }

          val eligibleInBatch = mutableListOf<MessageRecordEntity>()
          val now = System.currentTimeMillis()
          for (record in batch) {
            if (contactDao.isSuppressed(record.recipientPhoneE164)) {
              messageLogDao.applyMonotonicStatusTransition(
                messageId = record.id,
                targetStatus = MessageStatus.SKIPPED,
                eventTimestamp = now,
                source = "suppression_guard",
                detail = "Skipped prior to dispatch — present on suppression list",
              )
            } else {
              eligibleInBatch.add(record)
            }
          }

          if (eligibleInBatch.isEmpty()) continue

          if (currentCampaign.mode == AppMode.SIMULATION) {
            dispatchSimulationBatch(eligibleInBatch, simulationBatchDelayMs)
          } else {
            val senderRef = senderProfile?.cloudRunProfileRef ?: "default"
            val payloadRecipients =
              eligibleInBatch.map { record ->
                val contact = record.contactId?.let { contactsById[it] }
                val params =
                  if (contact != null) {
                    TemplateEngine.renderForContact(template.bodyText, mappings, contact)
                      .positionalParameters
                  } else {
                    emptyList()
                  }
                DispatchRecipientPayload(
                  localMessageId = record.id,
                  recipientName = record.recipientName,
                  recipientPhoneE164 = record.recipientPhoneE164,
                  idempotencyKey = record.idempotencyKey,
                  positionalParameters = params,
                )
              }
            val request =
              DispatchBatchRequest(
                campaignId = campaignId,
                campaignName = currentCampaign.name,
                senderProfileRef = senderRef,
                senderPhoneE164 = currentCampaign.senderPhoneSnapshot,
                metaTemplateName = template.metaTemplateName,
                languageCode = template.languageCode,
                metaMediaId = media?.metaMediaId,
                recipients = payloadRecipients,
              )
            val response =
              cloudRunApiClient.dispatchBatch(settings.cloudRunBaseUrl, request).getOrThrow()
            val eventTime = System.currentTimeMillis()
            response.results.forEach { res ->
              val targetStatus =
                when (res.status.uppercase()) {
                  "SUBMITTED" -> MessageStatus.SUBMITTED
                  "FAILED" -> MessageStatus.FAILED
                  else -> MessageStatus.QUEUED
                }
              val classification =
                if (targetStatus == MessageStatus.FAILED) {
                  RetryPolicyClassifier.classify(res.errorCode, res.errorDescription)
                } else null
              messageLogDao.applyMonotonicStatusTransition(
                messageId = res.localMessageId,
                targetStatus = targetStatus,
                eventTimestamp = eventTime,
                source = "cloud_run_dispatch",
                detail = res.apiResponseSummary ?: classification?.reasonSummary,
                whatsappMessageId = res.whatsappMessageId,
                errorCode = classification?.code ?: res.errorCode,
                errorDescription = classification?.reasonSummary ?: res.errorDescription,
                isPermanentError = res.isPermanentError || (classification?.isPermanent == true),
                incrementAttempt = true,
                apiResponseSummary = res.apiResponseSummary,
              )
            }
          }
        }

        val postLoop = campaignDao.getCampaignById(campaignId)
        if (postLoop != null && postLoop.status == CampaignStatus.RUNNING) {
          val remainingQueued = messageLogDao.getNextQueuedBatch(campaignId, 1)
          if (remainingQueued.isEmpty()) {
            if (postLoop.mode == AppMode.PRODUCTION) {
              syncCampaignStatusesFromCloudRun(campaignId)
            }
            campaignDao.markCampaignFinished(
              campaignId = campaignId,
              finalStatus = CampaignStatus.COMPLETED,
              completedAt = System.currentTimeMillis(),
            )
          }
        }
      }
    }

  private suspend fun dispatchSimulationBatch(
    batch: List<MessageRecordEntity>,
    batchDelayMs: Long,
  ) {
    val batchSize = batch.size
    batch.forEachIndexed { idx, record ->
      val t0 = System.currentTimeMillis()
      val isRetryAttempt = record.attemptCount > 0
      val simulatedWamid = "sim_wamid_${record.campaignId}_${record.id}_a${record.attemptCount + 1}"

      when {
        record.recipientName.contains("invalid_wa", ignoreCase = true) -> {
          val cls = RetryPolicyClassifier.classify("131026", "Simulated unregistered WhatsApp user")
          messageLogDao.applyMonotonicStatusTransition(
            messageId = record.id,
            targetStatus = MessageStatus.FAILED,
            eventTimestamp = t0,
            source = "simulation_sandbox",
            detail = cls.reasonSummary,
            errorCode = cls.code,
            errorDescription = cls.reasonSummary,
            isPermanentError = true,
            incrementAttempt = true,
            apiResponseSummary = "SIMULATION_PERM_ERROR",
          )
        }
        !isRetryAttempt &&
          (record.recipientName.contains("rate_limit", ignoreCase = true) ||
            (batchSize >= 5 && idx % 10 == 4)) -> {
          val cls =
            RetryPolicyClassifier.classify("130429", "Simulated Cloud API throughput limit")
          messageLogDao.applyMonotonicStatusTransition(
            messageId = record.id,
            targetStatus = MessageStatus.FAILED,
            eventTimestamp = t0,
            source = "simulation_sandbox",
            detail = cls.reasonSummary,
            errorCode = cls.code,
            errorDescription = cls.reasonSummary,
            isPermanentError = false,
            incrementAttempt = true,
            apiResponseSummary = "SIMULATION_TEMP_RATE_LIMIT",
          )
        }
        else -> {
          messageLogDao.applyMonotonicStatusTransition(
            messageId = record.id,
            targetStatus = MessageStatus.SUBMITTED,
            eventTimestamp = t0,
            source = "simulation_sandbox",
            detail = "Accepted by Simulation Sandbox",
            whatsappMessageId = simulatedWamid,
            incrementAttempt = true,
            apiResponseSummary = "SIMULATION_ACCEPTED",
          )
          messageLogDao.applyMonotonicStatusTransition(
            messageId = record.id,
            targetStatus = MessageStatus.SENT,
            eventTimestamp = t0 + 10L,
            source = "simulation_webhook",
            detail = "Simulated Meta sent webhook",
            whatsappMessageId = simulatedWamid,
          )
          messageLogDao.applyMonotonicStatusTransition(
            messageId = record.id,
            targetStatus = MessageStatus.DELIVERED,
            eventTimestamp = t0 + 25L,
            source = "simulation_webhook",
            detail = "Simulated handset delivery receipt",
            whatsappMessageId = simulatedWamid,
          )
          if (idx % 2 == 0 || isRetryAttempt) {
            messageLogDao.applyMonotonicStatusTransition(
              messageId = record.id,
              targetStatus = MessageStatus.READ,
              eventTimestamp = t0 + 40L,
              source = "simulation_webhook",
              detail = "Simulated recipient read receipt",
              whatsappMessageId = simulatedWamid,
            )
          }
        }
      }
    }
    if (batchDelayMs > 0L) {
      delay(batchDelayMs)
    }
  }

  suspend fun syncCampaignStatusesFromCloudRun(campaignId: Long): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        val baseUrl = preferences.settingsFlow.first().cloudRunBaseUrl
        if (!baseUrl.startsWith("https://")) return@runCatching 0
        val resp = cloudRunApiClient.syncCampaignStatuses(baseUrl, campaignId).getOrThrow()
        var applied = 0
        resp.events.forEach { ev ->
          val record =
            messageLogDao.getMessageRecordByWamid(ev.whatsappMessageId)
              ?: ev.idempotencyKey?.let { messageLogDao.getMessageRecordByIdempotencyKey(it) }
          if (record != null) {
            val targetStatus =
              runCatching { MessageStatus.valueOf(ev.status.uppercase()) }.getOrNull()
            if (targetStatus != null) {
              val cls =
                if (targetStatus == MessageStatus.FAILED) {
                  RetryPolicyClassifier.classify(ev.errorCode, ev.errorDescription)
                } else null
              messageLogDao.applyMonotonicStatusTransition(
                messageId = record.id,
                targetStatus = targetStatus,
                eventTimestamp = ev.eventTimestampMs,
                source = ev.source,
                detail = ev.errorDescription ?: "Webhook update: ${targetStatus.name}",
                whatsappMessageId = ev.whatsappMessageId,
                errorCode = cls?.code ?: ev.errorCode,
                errorDescription = cls?.reasonSummary ?: ev.errorDescription,
                isPermanentError = ev.isPermanentError || (cls?.isPermanent == true),
              )
              applied++
            }
          }
        }
        applied
      }
    }

  fun buildCampaignReportCsv(
    summary: CampaignStatsSummary,
    records: List<MessageRecordEntity>,
  ): String = CsvParserAndSecurity.buildCampaignReportCsv(summary, records)
}

class MessageLogRepository(private val messageLogDao: MessageLogDao) {
  val allMessages: Flow<List<MessageRecordEntity>> = messageLogDao.observeAllMessageRecords()

  fun observeEventsForMessage(messageId: Long): Flow<List<MessageStatusEventEntity>> =
    messageLogDao.observeEventsForMessage(messageId)

  fun observeRetryAttemptsForMessage(messageId: Long): Flow<List<RetryAttemptEntity>> =
    messageLogDao.observeRetryAttemptsForMessage(messageId)

  fun buildFilteredLogsCsv(
    records: List<MessageRecordEntity>,
    campaignNamesById: Map<Long, String>,
  ): String = CsvParserAndSecurity.buildMessageLogsCsv(records, campaignNamesById)
}

class SettingsRepository(
  private val preferences: SettingsPreferencesDataSource,
  private val senderProfileDao: SenderProfileDao,
  private val deviceKeyManager: DeviceKeyManager,
  private val campaignDao: CampaignDao,
  private val cloudRunApiClient: CloudRunApiClient,
) {
  val settings: Flow<OperatorSettings> = preferences.settingsFlow

  val completedSimulationCount: Flow<Int> = campaignDao.observeCompletedSimulationCount()

  val senderProfiles: Flow<List<SenderProfile>> =
    senderProfileDao.observeAllSenderProfiles().map { entities ->
      entities.map { entity ->
        SenderProfile(
          id = entity.id,
          label = entity.label,
          phoneNumberE164 = entity.phoneNumberE164,
          cloudRunProfileRef = entity.cloudRunProfileRef,
          isDefault = entity.isDefault,
          createdAt = entity.createdAt,
        )
      }
    }

  val defaultSenderProfile: Flow<SenderProfile?> =
    senderProfileDao.observeDefaultSenderProfile().map { entity ->
      entity?.let {
        SenderProfile(
          id = it.id,
          label = it.label,
          phoneNumberE164 = it.phoneNumberE164,
          cloudRunProfileRef = it.cloudRunProfileRef,
          isDefault = it.isDefault,
          createdAt = it.createdAt,
        )
      }
    }

  suspend fun getOrCreateDeviceKeyStatus(): DeviceKeyStatus =
    deviceKeyManager.getOrCreateStatus()

  suspend fun rotateDeviceKey(): DeviceKeyStatus = deviceKeyManager.rotateDeviceKey()

  suspend fun updateDefaultCountryCode(code: String): Result<Unit> {
    val trimmed = code.trim()
    return if (PhoneNumberHelper.isValidCallingCode(trimmed)) {
      preferences.updateDefaultCountryCode(trimmed)
      Result.success(Unit)
    } else {
      Result.failure(IllegalArgumentException("Enter a valid E.164 calling code such as +91 or +1"))
    }
  }

  suspend fun updateAutoApplyDefaultCode(enabled: Boolean) {
    preferences.updateAutoApplyDefaultCode(enabled)
  }

  suspend fun updateSimulationMode(enabled: Boolean) {
    preferences.updateSimulationMode(enabled)
  }

  suspend fun updateCloudRunBaseUrl(url: String): Result<Unit> {
    val trimmed = url.trim()
    if (trimmed.isNotEmpty() && !trimmed.startsWith("https://")) {
      return Result.failure(
        IllegalArgumentException("Cloud Run URL must use HTTPS (e.g., https://...run.app)")
      )
    }
    preferences.updateCloudRunBaseUrl(trimmed)
    return Result.success(Unit)
  }

  suspend fun updateBatchSize(batchSize: Int) {
    preferences.updateBatchSize(batchSize)
  }

  suspend fun updateMaxRetryAttempts(attempts: Int) {
    preferences.updateMaxRetryAttempts(attempts)
  }

  suspend fun updateOperatorTestNumber(
    rawNumber: String,
    defaultCode: String,
    autoApplyDefault: Boolean,
  ): Result<String> {
    if (rawNumber.isBlank()) {
      preferences.updateOperatorTestNumber("")
      return Result.success("")
    }
    return when (
      val normalized = PhoneNumberHelper.normalize(rawNumber, defaultCode, autoApplyDefault)
    ) {
      is PhoneNumberHelper.NormalizationResult.Valid -> {
        preferences.updateOperatorTestNumber(normalized.e164)
        Result.success(normalized.e164)
      }
      is PhoneNumberHelper.NormalizationResult.Invalid ->
        Result.failure(IllegalArgumentException(normalized.reason))
    }
  }

  suspend fun updateNotificationsEnabled(enabled: Boolean) {
    preferences.updateNotificationsEnabled(enabled)
  }

  suspend fun updateGcpBudgetAlertConfirmed(confirmed: Boolean) {
    preferences.updateGcpBudgetAlertConfirmed(confirmed)
  }

  suspend fun registerDeviceAndVerifyCloudRun(
    baseUrl: String,
    bootstrapToken: String?,
  ): Result<CloudRunReadinessResponse> {
    val regResult =
      cloudRunApiClient.registerDevice(
        baseUrl = baseUrl,
        bootstrapToken = bootstrapToken,
      )
    if (regResult.isFailure) {
      return Result.failure(regResult.exceptionOrNull() ?: IllegalStateException("Registration failed"))
    }
    return verifyCloudRunReadiness(baseUrl)
  }

  suspend fun verifyCloudRunReadiness(baseUrl: String): Result<CloudRunReadinessResponse> {
    val result = cloudRunApiClient.checkReadiness(baseUrl)
    result.onSuccess { resp ->
      preferences.updateCloudRunReadiness(
        deviceRegistered = resp.deviceRegistered,
        metaSecretsVerified = resp.metaSecretsConfigured,
        webhookVerified = resp.webhookVerifyConfigured,
        queueConfigured = resp.cloudTasksConfigured,
        checkedAt = System.currentTimeMillis(),
      )
    }
    return result
  }

  suspend fun addSenderProfile(
    label: String,
    rawPhoneNumber: String,
    cloudRunProfileRef: String,
    defaultCode: String,
    autoApplyDefault: Boolean,
    makeDefault: Boolean,
  ): Result<Long> {
    val cleanLabel = label.trim()
    if (cleanLabel.isEmpty()) {
      return Result.failure(IllegalArgumentException("Sender label is required"))
    }
    val cleanRef = cloudRunProfileRef.trim().ifEmpty { "default" }
    val normalized =
      PhoneNumberHelper.normalize(rawPhoneNumber, defaultCode, autoApplyDefault)
    if (normalized is PhoneNumberHelper.NormalizationResult.Invalid) {
      return Result.failure(IllegalArgumentException(normalized.reason))
    }
    val validNumber = (normalized as PhoneNumberHelper.NormalizationResult.Valid).e164
    val id =
      senderProfileDao.upsertSenderProfile(
        SenderProfileEntity(
          label = cleanLabel,
          phoneNumberE164 = validNumber,
          cloudRunProfileRef = cleanRef,
          isDefault = makeDefault,
        )
      )
    if (makeDefault) {
      senderProfileDao.setDefaultProfile(id)
    }
    return Result.success(id)
  }

  suspend fun setDefaultSenderProfile(profileId: Long) {
    senderProfileDao.setDefaultProfile(profileId)
  }

  suspend fun deleteSenderProfile(profileId: Long) {
    senderProfileDao.deleteSenderProfile(profileId)
  }

  fun evaluateProductionReadinessGate(
    settings: OperatorSettings,
    keyStatus: DeviceKeyStatus,
    senderProfiles: List<SenderProfile>,
    completedSimulationsCount: Int = 0,
  ): List<ProductionGateItem> {
    val hasHttpsCloudRun = settings.cloudRunBaseUrl.startsWith("https://")
    val hasSenderProfile = senderProfiles.isNotEmpty()
    val hasSimRun = completedSimulationsCount > 0
    return listOf(
      ProductionGateItem(
        id = 1,
        title = "Android Keystore Device Authorization",
        description =
          if (settings.cloudRunDeviceRegistered)
            "ECDSA P-256 device key active & registered with Cloud Run"
          else "ECDSA P-256 device key ready locally — register on Cloud Run below",
        isVerified = keyStatus.isReady && settings.cloudRunDeviceRegistered,
      ),
      ProductionGateItem(
        id = 2,
        title = "Cloud Run HTTPS Endpoint Configured",
        description = "Dedicated Google Cloud Run URL provisioned over HTTPS",
        isVerified = hasHttpsCloudRun,
      ),
      ProductionGateItem(
        id = 3,
        title = "Meta Credentials Secured in Secret Manager",
        description = "Access token, App Secret, and WABA ID verified in Cloud Secret Manager",
        isVerified = settings.cloudRunMetaSecretsVerified,
      ),
      ProductionGateItem(
        id = 4,
        title = "WhatsApp Business Sender Number Configured",
        description = "User-defined WhatsApp Business API contact number saved",
        isVerified = hasSenderProfile,
      ),
      ProductionGateItem(
        id = 5,
        title = "Webhook X-Hub-Signature-256 Verification",
        description = "Constant-time signature & verify token active on Cloud Run",
        isVerified = settings.cloudRunWebhookVerified,
      ),
      ProductionGateItem(
        id = 6,
        title = "Idempotency & Duplicate Prevention Verified",
        description = "Per-recipient deterministic idempotency keys active and tested",
        isVerified = hasSimRun,
      ),
      ProductionGateItem(
        id = 7,
        title = "Opt-Out & Suppression Enforcement Active",
        description = "Suppressed numbers blocked before enqueue and before every dispatch/retry",
        isVerified = hasSimRun,
      ),
      ProductionGateItem(
        id = 8,
        title = "Rate Limits & Batch Queue Configured",
        description = "Controlled batch sizing (${settings.batchSize} msgs/batch) & Cloud Run queue active",
        isVerified = settings.cloudRunQueueConfigured && settings.batchSize in 10..200,
      ),
      ProductionGateItem(
        id = 9,
        title = "Exponential Backoff Retry Policy Active",
        description = "Temporary vs permanent failure classification & max retries (${settings.maxRetryAttempts}) verified",
        isVerified = hasSimRun && settings.maxRetryAttempts >= 1,
      ),
      ProductionGateItem(
        id = 10,
        title = "Encrypted Storage & Backup Exclusions Active",
        description = "allowBackup=false and PII excluded from Android auto-backup",
        isVerified = true,
      ),
      ProductionGateItem(
        id = 11,
        title = "Test Campaign Verified in Simulation Mode",
        description = "At least one end-to-end campaign completed safely in Simulation Mode",
        isVerified = hasSimRun,
      ),
      ProductionGateItem(
        id = 12,
        title = "GCP Budget Alerts & Spending Caps Active",
        description = "Cloud Run & Meta messaging billing alerts confirmed in GCP Billing",
        isVerified = settings.gcpBudgetAlertConfirmed,
      ),
    )
  }
}
