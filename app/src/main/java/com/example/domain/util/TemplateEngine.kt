package com.example.domain.util

import com.example.domain.model.ContactWithFields
import com.example.domain.model.RenderedTemplatePreview
import com.example.domain.model.TemplateParameterMapping
import com.example.domain.model.TemplateValidationReport
import com.example.domain.model.TemplateValidationStatus

/**
 * Handles placeholder extraction ({Name}, {College}, {City}), CSV/Contact column matching,
 * Meta {{1}} positional parameter compilation, compatibility validation, and per-contact rendering.
 */
object TemplateEngine {

  const val MAX_META_BODY_CHARS = 1_024
  private val placeholderRegex = Regex("\\{([A-Za-z0-9_ ]{1,48})\\}")
  private val metaTemplateNameRegex = Regex("^[a-z0-9_]{1,512}$")

  val standardContactFieldKeys = listOf("Name", "Phone", "Email", "College", "City", "State")

  fun extractPlaceholders(bodyText: String): List<String> {
    val seen = LinkedHashSet<String>()
    placeholderRegex.findAll(bodyText).forEach { match ->
      val key = match.groupValues[1].trim()
      if (key.isNotEmpty()) seen.add(key)
    }
    return seen.toList()
  }

  fun suggestContactFieldMatch(
    placeholder: String,
    availableFields: List<String>,
  ): String {
    val normalizedTarget = placeholder.lowercase().replace(Regex("[^a-z0-9]"), "")
    val exact =
      availableFields.firstOrNull {
        it.lowercase().replace(Regex("[^a-z0-9]"), "") == normalizedTarget
      }
    if (exact != null) return exact
    val partial =
      availableFields.firstOrNull {
        val candidate = it.lowercase().replace(Regex("[^a-z0-9]"), "")
        candidate.contains(normalizedTarget) || normalizedTarget.contains(candidate)
      }
    return partial ?: placeholder
  }

  fun syncMappings(
    bodyText: String,
    existingMappings: List<TemplateParameterMapping>,
    availableFields: List<String>,
  ): List<TemplateParameterMapping> {
    val placeholders = extractPlaceholders(bodyText)
    val existingByKey = existingMappings.associateBy { it.placeholder }
    return placeholders.mapIndexed { index, placeholder ->
      val prev = existingByKey[placeholder]
      val matchedField =
        prev?.contactFieldKey?.takeIf { it.isNotBlank() }
          ?: suggestContactFieldMatch(placeholder, availableFields)
      TemplateParameterMapping(
        placeholder = placeholder,
        metaIndex = index + 1,
        contactFieldKey = matchedField,
        fallbackValue = prev?.fallbackValue.orEmpty(),
      )
    }
  }

  fun compileMetaBody(
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
  ): String {
    val indexByPlaceholder = mappings.associate { it.placeholder to it.metaIndex }
    return placeholderRegex.replace(bodyText) { match ->
      val key = match.groupValues[1].trim()
      val idx = indexByPlaceholder[key]
      if (idx != null) "{{$idx}}" else match.value
    }
  }

  fun validateTemplate(
    templateName: String,
    metaTemplateName: String,
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
    headerMediaRequired: Boolean,
    availableFields: List<String>,
  ): TemplateValidationReport {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    if (templateName.isBlank()) {
      errors.add("Template display name is required.")
    }
    val cleanMetaName = metaTemplateName.trim()
    if (cleanMetaName.isEmpty()) {
      errors.add("Meta approved template name is required (lowercase letters, digits, underscores).")
    } else if (!metaTemplateNameRegex.matches(cleanMetaName)) {
      errors.add(
        "Meta template name must contain only lowercase letters, numbers, and underscores."
      )
    }

    val cleanBody = bodyText.trim()
    if (cleanBody.isEmpty()) {
      errors.add("Message body cannot be empty.")
    } else if (cleanBody.length > MAX_META_BODY_CHARS) {
      errors.add("Message body exceeds Meta's $MAX_META_BODY_CHARS-character limit.")
    }

    // Detect malformed single braces like "{Name" or "Name}"
    val strippedValid = placeholderRegex.replace(bodyText, "")
    if (strippedValid.contains("{") || strippedValid.contains("}")) {
      warnings.add("Unclosed or nested curly braces detected outside valid {Placeholder} tokens.")
    }

    val compiledBody = compileMetaBody(bodyText, mappings)
    val placeholders = extractPlaceholders(bodyText)
    if (placeholders.isNotEmpty()) {
      val trimmedCompiled = compiledBody.trim()
      if (trimmedCompiled.startsWith("{{") || trimmedCompiled.endsWith("}}")) {
        warnings.add(
          "Meta recommends placing surrounding text before the first {{1}} and after the last parameter."
        )
      }
      val unmapped =
        mappings.filter { m ->
          m.contactFieldKey.isBlank() ||
            (m.contactFieldKey !in availableFields && m.fallbackValue.isBlank())
        }
      if (unmapped.isNotEmpty()) {
        warnings.add(
          "Placeholders without matching contact/CSV field or fallback: ${unmapped.joinToString { "{${it.placeholder}}" }}"
        )
      }
    }

    if (headerMediaRequired) {
      warnings.add(
        "Image-header template enabled: attach an approved JPEG/PNG flyer in Campaign Manager."
      )
    }

    val status =
      when {
        errors.isNotEmpty() -> TemplateValidationStatus.INVALID
        warnings.any { it.contains("without matching") } -> TemplateValidationStatus.NEEDS_MAPPING
        else -> TemplateValidationStatus.VALID
      }

    return TemplateValidationReport(
      status = status,
      errors = errors,
      warnings = warnings,
      metaCompiledBody = compiledBody,
    )
  }

  fun renderForContact(
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
    contact: ContactWithFields,
  ): RenderedTemplatePreview {
    val mappingByPlaceholder = mappings.associateBy { it.placeholder }
    val missing = mutableListOf<String>()
    val positionalValues = mutableListOf<String>()

    val rendered =
      placeholderRegex.replace(bodyText) { match ->
        val placeholderKey = match.groupValues[1].trim()
        val mapping = mappingByPlaceholder[placeholderKey]
        val fieldKey = mapping?.contactFieldKey ?: placeholderKey
        val rawValue = resolveContactFieldValue(contact, fieldKey)
        val resolvedValue =
          when {
            !rawValue.isNullOrBlank() -> rawValue
            !mapping?.fallbackValue.isNullOrBlank() -> {
              missing.add(placeholderKey)
              mapping!!.fallbackValue
            }
            else -> {
              missing.add(placeholderKey)
              "[missing:$placeholderKey]"
            }
          }
        resolvedValue
      }

    mappings.sortedBy { it.metaIndex }.forEach { mapping ->
      val rawValue = resolveContactFieldValue(contact, mapping.contactFieldKey)
      val finalParam =
        rawValue?.takeIf { it.isNotBlank() } ?: mapping.fallbackValue.ifBlank { "" }
      positionalValues.add(finalParam)
    }

    return RenderedTemplatePreview(
      renderedText = rendered,
      missingPlaceholders = missing.distinct(),
      positionalParameters = positionalValues,
    )
  }

  fun resolveContactFieldValue(contact: ContactWithFields, fieldKey: String): String? {
    val normalizedKey = fieldKey.trim().lowercase()
    return when (normalizedKey) {
      "name", "full name", "fullname" -> contact.name
      "phone", "phone_number", "mobile" -> contact.phoneE164
      "email" -> contact.email
      "college" -> contact.college
      "city" -> contact.city
      "state" -> contact.state
      else ->
        contact.customFields[fieldKey]
          ?: contact.customFields.entries
            .firstOrNull { it.key.equals(fieldKey, ignoreCase = true) }
            ?.value
    }
  }
}
