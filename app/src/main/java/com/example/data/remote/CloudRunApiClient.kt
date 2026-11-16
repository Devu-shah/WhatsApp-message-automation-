package com.example.data.remote

import android.util.Base64
import com.example.BuildConfig
import com.example.security.DeviceKeyManager
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Signed HTTPS client for Google Cloud Run.
 * Signs every request using Android Keystore ECDSA P-256 (timestamp + nonce + body SHA-256)
 * so no static Meta credentials or shared API keys are ever stored inside the APK.
 */
class CloudRunApiClient(
  private val deviceKeyManager: DeviceKeyManager,
  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(45, TimeUnit.SECONDS)
      .build(),
  private val allowInsecureForTests: Boolean = false,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun buildCanonicalSignatureHeaders(
    method: String,
    path: String,
    bodyBytes: ByteArray,
    timestampMs: Long = System.currentTimeMillis(),
    nonce: String = UUID.randomUUID().toString(),
  ): Map<String, String> {
    val keyStatus = deviceKeyManager.getOrCreateStatus()
    if (!keyStatus.isReady) {
      throw IllegalStateException(keyStatus.errorMessage ?: "Device signing key is not ready")
    }
    val bodySha256 = sha256Hex(bodyBytes)
    val canonical = "${method.uppercase()}\n$path\n$timestampMs\n$nonce\n$bodySha256"
    val signature = deviceKeyManager.signPayload(canonical.toByteArray(StandardCharsets.UTF_8))
    val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
    return mapOf(
      HEADER_KEY_FINGERPRINT to keyStatus.publicKeyFingerprint,
      HEADER_TIMESTAMP to timestampMs.toString(),
      HEADER_NONCE to nonce,
      HEADER_SIGNATURE to signatureB64,
    )
  }

  suspend fun registerDevice(
    baseUrl: String,
    bootstrapToken: String?,
    deviceLabel: String = "ReachHub Android Tablet",
  ): Result<DeviceRegistrationResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val status = deviceKeyManager.getOrCreateStatus()
        if (!status.isReady) {
          throw IllegalStateException(status.errorMessage ?: "Android Keystore key unavailable")
        }
        val payload =
          DeviceRegistrationRequest(
            keyFingerprint = status.publicKeyFingerprint,
            publicKeyBase64 = status.publicKeyBase64,
            deviceLabel = deviceLabel,
            appVersion = BuildConfig.VERSION_NAME,
          )
        val bodyBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        val extraHeaders =
          if (!bootstrapToken.isNullOrBlank()) {
            mapOf(HEADER_BOOTSTRAP_TOKEN to bootstrapToken.trim())
          } else {
            emptyMap()
          }
        val responseText =
          executeSignedRequest(
            baseUrl = baseUrl,
            method = "POST",
            path = "/api/v1/device/register",
            bodyBytes = bodyBytes,
            contentType = jsonMediaType,
            extraHeaders = extraHeaders,
          )
        json.decodeFromString<DeviceRegistrationResponse>(responseText)
      }
    }

  suspend fun checkReadiness(baseUrl: String): Result<CloudRunReadinessResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val responseText =
          executeSignedRequest(
            baseUrl = baseUrl,
            method = "GET",
            path = "/api/v1/readiness",
            bodyBytes = ByteArray(0),
            contentType = null,
          )
        json.decodeFromString<CloudRunReadinessResponse>(responseText)
      }
    }

  suspend fun uploadFlyerMedia(
    baseUrl: String,
    localUri: String,
    mimeType: String,
    sha256Hash: String,
    displayName: String,
    senderProfileRef: String,
  ): Result<MediaUploadResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val parsedUri = URI(localUri)
        val file =
          if (parsedUri.scheme == "file") File(parsedUri)
          else throw IllegalArgumentException("Unsupported flyer URI scheme for upload")
        if (!file.exists()) {
          throw IllegalStateException("Local flyer file no longer exists")
        }
        val fileBytes = file.readBytes()
        val multipart =
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("sha256Hash", sha256Hash)
            .addFormDataPart("senderProfileRef", senderProfileRef)
            .addFormDataPart("displayName", displayName)
            .addFormDataPart("file", displayName, file.asRequestBody(mimeType.toMediaType()))
            .build()

        // Sign metadata + content SHA-256 digest deterministically for multipart requests
        val canonicalBodyDigestSource =
          "$sha256Hash:$senderProfileRef:${fileBytes.size}".toByteArray(StandardCharsets.UTF_8)
        val cleanBase = validateBaseUrl(baseUrl)
        val path = "/api/v1/media/upload"
        val headers =
          buildCanonicalSignatureHeaders(
            method = "POST",
            path = path,
            bodyBytes = canonicalBodyDigestSource,
          )
        val requestBuilder =
          Request.Builder()
            .url("$cleanBase$path")
            .post(multipart)
            .addHeader("Accept", "application/json")
            .addHeader("X-ReachHub-Media-Sha256", sha256Hash)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
          val text = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw IllegalStateException("Cloud Run upload failed (${response.code}): ${text.take(240)}")
          }
          json.decodeFromString<MediaUploadResponse>(text)
        }
      }
    }

  suspend fun dispatchBatch(
    baseUrl: String,
    payload: DispatchBatchRequest,
  ): Result<DispatchBatchResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val bodyBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        val responseText =
          executeSignedRequest(
            baseUrl = baseUrl,
            method = "POST",
            path = "/api/v1/campaigns/${payload.campaignId}/dispatch",
            bodyBytes = bodyBytes,
            contentType = jsonMediaType,
          )
        json.decodeFromString<DispatchBatchResponse>(responseText)
      }
    }

  suspend fun controlCampaign(
    baseUrl: String,
    campaignId: Long,
    action: String,
  ): Result<CampaignControlResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val bodyBytes =
          json.encodeToString(CampaignControlRequest(action)).toByteArray(StandardCharsets.UTF_8)
        val responseText =
          executeSignedRequest(
            baseUrl = baseUrl,
            method = "POST",
            path = "/api/v1/campaigns/$campaignId/control",
            bodyBytes = bodyBytes,
            contentType = jsonMediaType,
          )
        json.decodeFromString<CampaignControlResponse>(responseText)
      }
    }

  suspend fun syncCampaignStatuses(
    baseUrl: String,
    campaignId: Long,
    sinceTimestampMs: Long = 0L,
  ): Result<CampaignStatusSyncResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val path = "/api/v1/campaigns/$campaignId/status"
        val querySuffix = if (sinceTimestampMs > 0L) "?since=$sinceTimestampMs" else ""
        val cleanBase = validateBaseUrl(baseUrl)
        val headers =
          buildCanonicalSignatureHeaders(method = "GET", path = path, bodyBytes = ByteArray(0))
        val requestBuilder =
          Request.Builder().url("$cleanBase$path$querySuffix").get().addHeader("Accept", "application/json")
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
          val text = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw IllegalStateException("Status sync failed (${response.code}): ${text.take(240)}")
          }
          json.decodeFromString<CampaignStatusSyncResponse>(text)
        }
      }
    }

  private suspend fun executeSignedRequest(
    baseUrl: String,
    method: String,
    path: String,
    bodyBytes: ByteArray,
    contentType: okhttp3.MediaType?,
    extraHeaders: Map<String, String> = emptyMap(),
  ): String {
    val cleanBase = validateBaseUrl(baseUrl)
    val sigHeaders =
      buildCanonicalSignatureHeaders(method = method, path = path, bodyBytes = bodyBytes)
    val requestBody: RequestBody? =
      if (method.equals("GET", ignoreCase = true)) null
      else bodyBytes.toRequestBody(contentType)

    val builder =
      Request.Builder()
        .url("$cleanBase$path")
        .method(method.uppercase(), requestBody)
        .addHeader("Accept", "application/json")
    sigHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
    extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }

    httpClient.newCall(builder.build()).execute().use { response ->
      val text = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException("Cloud Run error (${response.code}): ${text.take(240)}")
      }
      return text
    }
  }

  private fun validateBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) {
      throw IllegalArgumentException("Google Cloud Run Base URL is not configured in Settings")
    }
    if (!allowInsecureForTests && !trimmed.startsWith("https://")) {
      throw IllegalArgumentException("Google Cloud Run endpoint must use HTTPS")
    }
    return trimmed
  }

  companion object {
    const val HEADER_KEY_FINGERPRINT = "X-ReachHub-Key-Fingerprint"
    const val HEADER_TIMESTAMP = "X-ReachHub-Timestamp"
    const val HEADER_NONCE = "X-ReachHub-Nonce"
    const val HEADER_SIGNATURE = "X-ReachHub-Signature"
    const val HEADER_BOOTSTRAP_TOKEN = "X-ReachHub-Bootstrap-Token"

    fun sha256Hex(bytes: ByteArray): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
      return digest.joinToString("") { "%02x".format(it) }
    }
  }
}
