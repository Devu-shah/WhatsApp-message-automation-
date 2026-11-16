package com.example.ui.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.MediaAsset
import com.example.ui.components.EmptyStateCard

@Composable
fun MediaLibraryScreen(
  viewModel: MediaLibraryViewModel,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var assetPendingDelete by remember { mutableStateOf<MediaAsset?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        viewModel.importFlyer(context.contentResolver, context.filesDir, uri)
      }
    }

  LaunchedEffect(uiState.feedbackMessage) {
    val msg = uiState.feedbackMessage
    if (msg != null) {
      onShowMessage(msg)
      viewModel.clearFeedback()
    }
  }

  val selectedAsset =
    uiState.assets.firstOrNull { it.id == uiState.selectedAssetId } ?: uiState.assets.firstOrNull()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isSplitPane = maxWidth >= 720.dp

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Media & Flyer Library", style = MaterialTheme.typography.headlineMedium)
          Text(
            text =
              "Zero-permission Android Photo Picker • Verified JPEG/PNG up to 5 MB • SHA-256 deduplicated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          enabled = !uiState.isImporting,
          modifier = Modifier.testTag("pick_flyer_image_button"),
        ) {
          if (uiState.isImporting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              Icons.Outlined.AddPhotoAlternate,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
          Spacer(Modifier.width(8.dp))
          Text("Add Flyer")
        }
      }

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text =
              "WhatsApp Cloud API policy: Business-initiated flyers require an approved Image-Header template. Free-form captions only apply within an active 24-hour customer service window.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (uiState.assets.isEmpty()) {
        EmptyStateCard(
          icon = Icons.Outlined.PermMedia,
          title = "No flyers saved yet",
          description =
            "Select a JPEG or PNG campaign flyer using the Android Photo Picker. Validated flyers are hashed and cached for Meta media_id reuse.",
          actionLabel = "Select Flyer Image",
          onAction = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
        )
      } else if (isSplitPane) {
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          LazyColumn(
            modifier = Modifier.weight(0.95f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(uiState.assets, key = { it.id }) { asset ->
              MediaAssetListCard(
                asset = asset,
                selected = selectedAsset?.id == asset.id,
                onClick = { viewModel.selectAsset(asset.id) },
              )
            }
          }
          Box(modifier = Modifier.weight(1.25f).fillMaxHeight()) {
            selectedAsset?.let { asset ->
              MediaInspectorPane(
                asset = asset,
                onSaveMetadata = { name, caption ->
                  viewModel.updateMetadata(asset.id, name, caption)
                },
                onDelete = { assetPendingDelete = asset },
              )
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(uiState.assets, key = { it.id }) { asset ->
            MediaAssetListCard(
              asset = asset,
              selected = selectedAsset?.id == asset.id,
              onClick = { viewModel.selectAsset(asset.id) },
            )
          }
          selectedAsset?.let { asset ->
            item {
              MediaInspectorPane(
                asset = asset,
                onSaveMetadata = { name, caption ->
                  viewModel.updateMetadata(asset.id, name, caption)
                },
                onDelete = { assetPendingDelete = asset },
              )
            }
          }
        }
      }
    }
  }

  assetPendingDelete?.let { asset ->
    AlertDialog(
      onDismissRequest = { assetPendingDelete = null },
      title = { Text("Delete Flyer?") },
      text = { Text("Remove '${asset.displayName}' from local storage and Media Library?") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            viewModel.deleteFlyer(asset)
            assetPendingDelete = null
          },
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { assetPendingDelete = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun MediaAssetListCard(
  asset: MediaAsset,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surface
      ),
    border =
      BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AsyncImage(
        model = asset.localUri,
        contentDescription = asset.displayName,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(64.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = asset.displayName,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${asset.mimeType} • ${asset.widthPx}×${asset.heightPx} • ${formatBytes(asset.byteSize)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = asset.metaMediaId?.let { "Meta ID: $it" } ?: asset.uploadStatus.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun MediaInspectorPane(
  asset: MediaAsset,
  onSaveMetadata: (displayName: String, captionNote: String?) -> Unit,
  onDelete: () -> Unit,
) {
  var displayName by remember(asset.id) { mutableStateOf(asset.displayName) }
  var captionNote by remember(asset.id) { mutableStateOf(asset.captionNote ?: "") }

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxSize(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Flyer Preview & Metadata", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_flyer_button")) {
          Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = "Delete flyer",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      AsyncImage(
        model = asset.localUri,
        contentDescription = asset.displayName,
        contentScale = ContentScale.Fit,
        modifier =
          Modifier.fillMaxWidth()
            .height(240.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
      )

      Text(
        text =
          "Verified ${asset.mimeType} • ${asset.widthPx}×${asset.heightPx}px • ${formatBytes(asset.byteSize)}\nSHA-256: ${asset.sha256Hash.take(24)}...",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider()

      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text("Flyer Display Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = captionNote,
        onValueChange = { captionNote = it },
        label = { Text("Optional Caption Note (24h Window Only)") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )

      Button(
        onClick = { onSaveMetadata(displayName, captionNote) },
        modifier = Modifier.testTag("save_flyer_metadata_button"),
      ) {
        Text("Save Flyer Details")
      }
    }
  }
}

private fun formatBytes(bytes: Long): String =
  when {
    bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
  }
