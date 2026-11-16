package com.example.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ContactRepository
import com.example.data.repository.TemplateRepository
import com.example.domain.model.ContactWithFields
import com.example.domain.model.MessageTemplate
import com.example.domain.model.RenderedTemplatePreview
import com.example.domain.model.TemplateParameterMapping
import com.example.domain.model.TemplateValidationReport
import com.example.domain.util.TemplateEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesUiState(
  val templates: List<MessageTemplate> = emptyList(),
  val availableContactFields: List<String> = TemplateEngine.standardContactFieldKeys,
  val contactsForPreview: List<ContactWithFields> = emptyList(),
  val selectedTemplateId: Long? = null,
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
)

class TemplatesViewModel(
  private val templateRepository: TemplateRepository,
  contactRepository: ContactRepository,
) : ViewModel() {

  private val selectedTemplateIdFlow = MutableStateFlow<Long?>(null)
  private val feedbackFlow = MutableStateFlow<Pair<String, Boolean>?>(null)

  val uiState: StateFlow<TemplatesUiState> =
    combine(
        templateRepository.templates,
        contactRepository.contactsWithFields,
        contactRepository.distinctCustomFieldKeys,
        selectedTemplateIdFlow,
        feedbackFlow,
      ) { templates, contacts, customKeys, selectedId, feedback ->
        val allFields =
          (TemplateEngine.standardContactFieldKeys + customKeys).distinct()
        val resolvedSelected =
          if (selectedId == null) templates.firstOrNull()?.id else selectedId
        TemplatesUiState(
          templates = templates,
          availableContactFields = allFields,
          contactsForPreview = contacts.filter { !it.isOptedOut },
          selectedTemplateId = resolvedSelected,
          feedbackMessage = feedback?.first,
          isError = feedback?.second ?: false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TemplatesUiState(),
      )

  fun selectTemplate(templateId: Long?) {
    selectedTemplateIdFlow.value = templateId
  }

  fun validateDraft(
    name: String,
    metaName: String,
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
    headerMediaRequired: Boolean,
  ): TemplateValidationReport =
    TemplateEngine.validateTemplate(
      templateName = name,
      metaTemplateName = metaName,
      bodyText = bodyText,
      mappings = mappings,
      headerMediaRequired = headerMediaRequired,
      availableFields = uiState.value.availableContactFields,
    )

  fun renderPreview(
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
    contact: ContactWithFields,
  ): RenderedTemplatePreview = TemplateEngine.renderForContact(bodyText, mappings, contact)

  fun countContactsWithMissingValues(
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
  ): Int {
    val contacts = uiState.value.contactsForPreview
    if (contacts.isEmpty() || mappings.isEmpty()) return 0
    return contacts.count { contact ->
      TemplateEngine.renderForContact(bodyText, mappings, contact).missingPlaceholders.isNotEmpty()
    }
  }

  fun saveTemplate(
    id: Long,
    name: String,
    metaTemplateName: String,
    languageCode: String,
    category: String,
    bodyText: String,
    mappings: List<TemplateParameterMapping>,
    headerMediaRequired: Boolean,
  ) {
    viewModelScope.launch {
      templateRepository
        .saveTemplate(
          id = id,
          name = name,
          metaTemplateName = metaTemplateName,
          languageCode = languageCode,
          category = category,
          bodyText = bodyText,
          mappings = mappings,
          headerMediaRequired = headerMediaRequired,
          availableFields = uiState.value.availableContactFields,
        )
        .onSuccess { savedId ->
          selectedTemplateIdFlow.value = savedId
          feedbackFlow.value = "Saved template '$name'" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Unable to save template") to true
        }
    }
  }

  fun duplicateTemplate(template: MessageTemplate) {
    viewModelScope.launch {
      templateRepository
        .duplicateTemplate(template)
        .onSuccess { newId ->
          selectedTemplateIdFlow.value = newId
          feedbackFlow.value = "Duplicated template '${template.name}'" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Unable to duplicate template") to true
        }
    }
  }

  fun deleteTemplate(templateId: Long) {
    viewModelScope.launch {
      templateRepository
        .deleteTemplate(templateId)
        .onSuccess {
          if (selectedTemplateIdFlow.value == templateId) {
            selectedTemplateIdFlow.value = null
          }
          feedbackFlow.value = "Template deleted" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Unable to delete template") to true
        }
    }
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(
    private val templateRepository: TemplateRepository,
    private val contactRepository: ContactRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      TemplatesViewModel(templateRepository, contactRepository) as T
  }
}
