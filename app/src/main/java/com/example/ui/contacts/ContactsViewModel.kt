package com.example.ui.contacts

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ContactRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactFilter
import com.example.domain.model.ContactWithFields
import com.example.domain.model.OperatorSettings
import com.example.domain.model.SuppressionRecord
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactsUiState(
  val allContactsCount: Int = 0,
  val eligibleCount: Int = 0,
  val optedOutCount: Int = 0,
  val filteredContacts: List<ContactWithFields> = emptyList(),
  val suppressionList: List<SuppressionRecord> = emptyList(),
  val searchQuery: String = "",
  val activeFilter: ContactFilter = ContactFilter.ALL,
  val selectedContact: ContactWithFields? = null,
  val settings: OperatorSettings = OperatorSettings(),
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
)

class ContactsViewModel(
  private val contactRepository: ContactRepository,
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  private val searchQueryFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(ContactFilter.ALL)
  private val selectedIdFlow = MutableStateFlow<Long?>(null)
  private val feedbackFlow = MutableStateFlow<Pair<String, Boolean>?>(null)

  val uiState: StateFlow<ContactsUiState> =
    combine(
        combine(
          contactRepository.contactsWithFields,
          contactRepository.suppressionList,
          settingsRepository.settings,
        ) { contacts, suppressions, settings ->
          Triple(contacts, suppressions, settings)
        },
        combine(searchQueryFlow, filterFlow, selectedIdFlow, feedbackFlow) {
          query,
          filter,
          selectedId,
          feedback ->
          FilterParams(query, filter, selectedId, feedback)
        },
      ) { (contacts, suppressions, settings), params ->
        val query = params.query.trim().lowercase()
        val filtered =
          contacts.filter { contact ->
            val matchesFilter =
              when (params.filter) {
                ContactFilter.ALL -> true
                ContactFilter.ELIGIBLE ->
                  !contact.isOptedOut && contact.consentStatus != ConsentStatus.REVOKED
                ContactFilter.OPTED_OUT -> contact.isOptedOut
                ContactFilter.CSV_IMPORTED ->
                  contact.importStatus == com.example.domain.model.ContactImportStatus.CSV_IMPORTED ||
                    contact.importStatus == com.example.domain.model.ContactImportStatus.CSV_UPDATED
                ContactFilter.MANUAL ->
                  contact.importStatus == com.example.domain.model.ContactImportStatus.MANUAL
                ContactFilter.CONSENT_GRANTED ->
                  !contact.isOptedOut && contact.consentStatus == ConsentStatus.GRANTED
                ContactFilter.CONSENT_PENDING_OR_REVOKED ->
                  contact.isOptedOut || contact.consentStatus != ConsentStatus.GRANTED
              }
            val matchesQuery =
              if (query.isEmpty()) true
              else {
                contact.name.lowercase().contains(query) ||
                  contact.phoneE164.lowercase().contains(query) ||
                  (contact.college?.lowercase()?.contains(query) == true) ||
                  (contact.city?.lowercase()?.contains(query) == true) ||
                  (contact.email?.lowercase()?.contains(query) == true) ||
                  contact.customFields.values.any { it.lowercase().contains(query) }
              }
            matchesFilter && matchesQuery
          }

        val selected =
          filtered.firstOrNull { it.id == params.selectedId } ?: filtered.firstOrNull()

        ContactsUiState(
          allContactsCount = contacts.size,
          eligibleCount = contacts.count { !it.isOptedOut },
          optedOutCount = suppressions.size,
          filteredContacts = filtered,
          suppressionList = suppressions,
          searchQuery = params.query,
          activeFilter = params.filter,
          selectedContact = selected,
          settings = settings,
          feedbackMessage = params.feedback?.first,
          isError = params.feedback?.second ?: false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContactsUiState(),
      )

  private data class FilterParams(
    val query: String,
    val filter: ContactFilter,
    val selectedId: Long?,
    val feedback: Pair<String, Boolean>?,
  )

  fun updateSearchQuery(query: String) {
    searchQueryFlow.value = query
  }

  fun updateFilter(filter: ContactFilter) {
    filterFlow.value = filter
  }

  fun selectContact(contactId: Long) {
    selectedIdFlow.value = contactId
  }

  fun saveContact(
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
  ) {
    viewModelScope.launch {
      val settings = uiState.value.settings
      contactRepository
        .saveContact(
          id = id,
          name = name,
          rawPhone = rawPhone,
          email = email,
          college = college,
          city = city,
          state = state,
          consentStatus = consentStatus,
          isOptedOut = isOptedOut,
          customFields = customFields,
          defaultCountryCode = settings.defaultCountryCode,
          autoApplyDefaultCode = settings.autoApplyDefaultCountryCode,
        )
        .onSuccess { savedId ->
          selectedIdFlow.value = savedId
          feedbackFlow.value =
            (if (id == 0L) "Added contact" else "Updated contact") to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Unable to save contact") to true
        }
    }
  }

  fun deleteContact(contactId: Long) {
    viewModelScope.launch {
      contactRepository.deleteContact(contactId)
      if (selectedIdFlow.value == contactId) {
        selectedIdFlow.value = null
      }
      feedbackFlow.value = "Contact deleted" to false
    }
  }

  fun toggleOptOut(contact: ContactWithFields, optedOut: Boolean) {
    viewModelScope.launch {
      contactRepository.setOptOut(
        contactId = contact.id,
        phoneE164 = contact.phoneE164,
        optedOut = optedOut,
      )
      feedbackFlow.value =
        if (optedOut) "${contact.name} added to suppression list" to false
        else "${contact.name} restored from suppression list" to false
    }
  }

  fun addManualSuppression(rawPhone: String, reason: String) {
    viewModelScope.launch {
      val settings = uiState.value.settings
      contactRepository
        .addSuppressionNumber(
          rawPhone = rawPhone,
          reason = reason,
          defaultCountryCode = settings.defaultCountryCode,
          autoApplyDefaultCode = settings.autoApplyDefaultCountryCode,
        )
        .onSuccess { e164 ->
          feedbackFlow.value = "Added $e164 to suppression list" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Invalid phone number") to true
        }
    }
  }

  fun removeSuppression(phoneE164: String) {
    viewModelScope.launch {
      contactRepository.removeSuppression(phoneE164)
      feedbackFlow.value = "Removed $phoneE164 from suppression list" to false
    }
  }

  fun exportFilteredToUri(contentResolver: ContentResolver, uri: Uri) {
    viewModelScope.launch {
      val listToExport = uiState.value.filteredContacts
      if (listToExport.isEmpty()) {
        feedbackFlow.value = "No contacts to export for the current filter" to true
        return@launch
      }
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            val csvText = contactRepository.exportContactsCsv(listToExport)
            contentResolver.openOutputStream(uri)?.use { out ->
              out.write(csvText.toByteArray(StandardCharsets.UTF_8))
              out.flush()
            } ?: error("Unable to open destination file")
          }
        }
      result
        .onSuccess {
          feedbackFlow.value = "Exported ${listToExport.size} contacts to CSV" to false
        }
        .onFailure { err ->
          feedbackFlow.value = "CSV export failed: ${err.message ?: "Unknown error"}" to true
        }
    }
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(
    private val contactRepository: ContactRepository,
    private val settingsRepository: SettingsRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ContactsViewModel(contactRepository, settingsRepository) as T
  }
}
