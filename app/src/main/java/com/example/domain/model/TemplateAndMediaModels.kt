package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TemplateParameterMapping(
  val placeholder: String,
  val metaIndex: Int,
  val contactFieldKey: String,
  val fallbackValue: String = "",
)

data class MessageTemplate(
  val id: Long = 0,
  val name: String,
  val metaTemplateName: String,
  val languageCode: String = "en",
  val category: String = "MARKETING",
  val bodyText: String,
  val placeholders: List<String> = emptyList(),
  val mappings: List<TemplateParameterMapping> = emptyList(),
  val headerMediaRequired: Boolean = false,
  val validationStatus: TemplateValidationStatus = TemplateValidationStatus.NEEDS_MAPPING,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class TemplateValidationReport(
  val status: TemplateValidationStatus,
  val errors: List<String>,
  val warnings: List<String>,
  val metaCompiledBody: String,
)

data class RenderedTemplatePreview(
  val renderedText: String,
  val missingPlaceholders: List<String>,
  val positionalParameters: List<String>,
)

data class MediaAsset(
  val id: Long = 0,
  val displayName: String,
  val localUri: String,
  val mimeType: String,
  val byteSize: Long,
  val sha256Hash: String,
  val metaMediaId: String? = null,
  val captionNote: String? = null,
  val widthPx: Int = 0,
  val heightPx: Int = 0,
  val uploadStatus: MediaUploadStatus = MediaUploadStatus.LOCAL_ONLY,
  val uploadedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)
