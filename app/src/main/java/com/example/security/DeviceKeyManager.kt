package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.domain.model.DeviceKeyStatus
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DeviceKeyManager {
  suspend fun getOrCreateStatus(): DeviceKeyStatus

  suspend fun signPayload(payload: ByteArray): ByteArray

  suspend fun rotateDeviceKey(): DeviceKeyStatus
}

/**
 * Manages a non-exportable ECDSA P-256 key pair inside Android Keystore.
 * Used in Phase 6+ to cryptographically sign requests to Google Cloud Run without
 * embedding shared API keys inside the APK.
 */
class AndroidKeystoreDeviceKeyManager(
  private val keyAlias: String = DEFAULT_KEY_ALIAS
) : DeviceKeyManager {

  override suspend fun getOrCreateStatus(): DeviceKeyStatus =
    withContext(Dispatchers.IO) {
      runCatching {
          val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
          if (!keyStore.containsAlias(keyAlias)) {
            generateKeyPair()
          }
          readKeyStatus(keyStore)
        }
        .getOrElse { error ->
          DeviceKeyStatus(
            isReady = false,
            keyAlias = keyAlias,
            errorMessage = error.message ?: "Android Keystore unavailable in this runtime",
          )
        }
    }

  override suspend fun signPayload(payload: ByteArray): ByteArray =
    withContext(Dispatchers.IO) {
      val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      if (!keyStore.containsAlias(keyAlias)) {
        generateKeyPair()
      }
      val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
        ?: error("PrivateKeyEntry missing for alias $keyAlias")
      val privateKey: PrivateKey = entry.privateKey
      Signature.getInstance(SIGNATURE_ALGORITHM).run {
        initSign(privateKey)
        update(payload)
        sign()
      }
    }

  override suspend fun rotateDeviceKey(): DeviceKeyStatus =
    withContext(Dispatchers.IO) {
      runCatching {
          val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
          if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
          }
          generateKeyPair()
          readKeyStatus(keyStore)
        }
        .getOrElse { error ->
          DeviceKeyStatus(
            isReady = false,
            keyAlias = keyAlias,
            errorMessage = error.message ?: "Unable to rotate device key",
          )
        }
    }

  private fun generateKeyPair() {
    val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          keyAlias,
          KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .build()
    generator.initialize(spec)
    generator.generateKeyPair()
  }

  private fun readKeyStatus(keyStore: KeyStore): DeviceKeyStatus {
    val certificate = keyStore.getCertificate(keyAlias)
      ?: return DeviceKeyStatus(
        isReady = false,
        keyAlias = keyAlias,
        errorMessage = "Certificate not found for device key",
      )
    val encodedPublicKey = certificate.publicKey.encoded
    val digestBytes = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
    val fingerprint = digestBytes.joinToString(":") { "%02X".format(it) }
    val publicKeyBase64 = Base64.encodeToString(encodedPublicKey, Base64.NO_WRAP)
    return DeviceKeyStatus(
      isReady = true,
      keyAlias = keyAlias,
      publicKeyFingerprint = fingerprint,
      publicKeyBase64 = publicKeyBase64,
      errorMessage = null,
    )
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DEFAULT_KEY_ALIAS = "reachhub_operator_ecdsa_p256"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
  }
}
