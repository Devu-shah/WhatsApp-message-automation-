package com.example.domain.util

import java.security.MessageDigest

/**
 * Validates flyer image bytes via magic-byte signatures (JPEG / PNG) and size limits,
 * never trusting filename extensions.
 */
object ImageMediaSecurity {
  const val MAX_IMAGE_BYTES: Long = 5L * 1024L * 1024L // Meta WhatsApp 5 MB image cap

  fun detectVerifiedImageMime(bytes: ByteArray): String? {
    if (bytes.size < 8) return null
    // JPEG: FF D8 FF
    val isJpeg =
      (bytes[0].toInt() and 0xFF) == 0xFF &&
        (bytes[1].toInt() and 0xFF) == 0xD8 &&
        (bytes[2].toInt() and 0xFF) == 0xFF
    if (isJpeg) return "image/jpeg"

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    val pngSig = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val isPng = pngSig.indices.all { i -> (bytes[i].toInt() and 0xFF) == pngSig[i] }
    if (isPng) return "image/png"

    return null
  }

  fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun sanitizeDisplayName(rawName: String?): String {
    val base =
      rawName
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        ?.trim()
        .orEmpty()
    return base.ifEmpty { "flyer_${System.currentTimeMillis()}" }.take(80)
  }
}
