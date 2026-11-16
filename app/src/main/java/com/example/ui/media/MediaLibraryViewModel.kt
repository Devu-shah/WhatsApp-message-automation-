package com.example.ui.media

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MediaRepository
import com.example.domain.model.MediaAsset
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MediaLibraryUiState(
  val assets: List<MediaAsset> = emptyList(),
  val selectedAssetId: Long? = null,
  val isImporting: Boolean = false,
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
)

class MediaLibraryViewModel(private val mediaRepository: MediaRepository) : ViewModel() {

  private val selectedIdFlow = MutableStateFlow<Long?>(null)
  private val importingFlow = MutableStateFlow(false)
  private val feedbackFlow = MutableStateFlow<Pair<String, Boolean>?>(null)

  val uiState: StateFlow<MediaLibraryUiState> =
    combine(
        mediaRepository.mediaAssets,
        selectedIdFlow,
        importingFlow,
        feedbackFlow,
      ) { assets, selectedId, importing, feedback ->
        val resolvedSelected = selectedId ?: assets.firstOrNull()?.id
        MediaLibraryUiState(
          assets = assets,
          selectedAssetId = resolvedSelected,
          isImporting = importing,
          feedbackMessage = feedback?.first,
          isError = feedback?.second ?: false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MediaLibraryUiState(),
      )

  fun selectAsset(assetId: Long) {
    selectedIdFlow.value = assetId
  }

  fun importFlyer(contentResolver: ContentResolver, filesDir: File, uri: Uri) {
    viewModelScope.launch {
      importingFlow.value = true
      mediaRepository
        .importFlyerFromUri(contentResolver = contentResolver, filesDir = filesDir, sourceUri = uri)
        .onSuccess { (asset, wasDeduplicated) ->
          selectedIdFlow.value = asset.id
          feedbackFlow.value =
            if (wasDeduplicated) "Flyer already saved — reused '${asset.displayName}'" to false
            else "Saved verified flyer '${asset.displayName}'" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Failed to import flyer") to true
        }
      importingFlow.value = false
    }
  }

  fun updateMetadata(id: Long, displayName: String, captionNote: String?) {
    viewModelScope.launch {
      mediaRepository.updateFlyerMetadata(id, displayName, captionNote)
      feedbackFlow.value = "Updated flyer metadata" to false
    }
  }

  fun deleteFlyer(asset: MediaAsset) {
    viewModelScope.launch {
      mediaRepository
        .deleteFlyer(asset)
        .onSuccess {
          if (selectedIdFlow.value == asset.id) {
            selectedIdFlow.value = null
          }
          feedbackFlow.value = "Removed flyer '${asset.displayName}'" to false
        }
        .onFailure { err ->
          feedbackFlow.value = (err.message ?: "Cannot delete flyer") to true
        }
    }
  }

  fun clearFeedback() {
    feedbackFlow.value = null
  }

  class Factory(private val mediaRepository: MediaRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MediaLibraryViewModel(mediaRepository) as T
  }
}
