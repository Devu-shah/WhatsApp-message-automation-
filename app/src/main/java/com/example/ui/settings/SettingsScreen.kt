package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.domain.util.PhoneNumberHelper
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var callingCodeInput by remember(uiState.settings.defaultCountryCode) {
    mutableStateOf(uiState.settings.defaultCountryCode)
  }
  var cloudRunUrlInput by remember(uiState.settings.cloudRunBaseUrl) {
    mutableStateOf(uiState.settings.cloudRunBaseUrl)
  }
  var testNumberInput by remember(uiState.settings.operatorTestNumber) {
    mutableStateOf(uiState.settings.operatorTestNumber)
  }
  var sampleCsvPhone by remember { mutableStateOf("9876543210") }
  var batchSizeSlider by remember(uiState.settings.batchSize) {
    mutableFloatStateOf(uiState.settings.batchSize.toFloat())
  }
  var maxRetriesSlider by remember(uiState.settings.maxRetryAttempts) {
    mutableFloatStateOf(uiState.settings.maxRetryAttempts.toFloat())
  }
  var showAddSenderDialog by remember { mutableStateOf(false) }
  var showRotateKeyDialog by remember { mutableStateOf(false) }
  var showRegisterCloudRunDialog by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.feedbackMessage) {
    val message = uiState.feedbackMessage
    if (message != null) {
      onShowMessage(message)
      viewModel.clearFeedback()
    }
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Settings & Readiness", style = MaterialTheme.typography.headlineMedium)
        Text(
          text =
            "Configure your WhatsApp Business sender numbers, +91 calling-code rules, Android Keystore device identity, and Cloud Run backend.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // 1. User-Defined WhatsApp Business Contact Numbers
      SettingsSectionCard(
        title = "WhatsApp Business Contact Numbers",
        subtitle =
          "Save your authorized WhatsApp Business API sender numbers. Meta Access Tokens stay in Google Cloud Secret Manager and are resolved server-side by profile reference.",
        icon = Icons.Outlined.Phone,
      ) {
        if (uiState.senderProfiles.isEmpty()) {
          Text(
            text = "No WhatsApp Business sender number added yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          uiState.senderProfiles.forEachIndexed { index, profile ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(profile.label, style = MaterialTheme.typography.titleSmall)
                  if (profile.isDefault) {
                    Surface(
                      color = MaterialTheme.colorScheme.primaryContainer,
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                  }
                }
                Text(
                  text = "${profile.phoneNumberE164}  •  Cloud Run ref: ${profile.cloudRunProfileRef}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (!profile.isDefault) {
                  TextButton(onClick = { viewModel.setDefaultSenderProfile(profile.id) }) {
                    Text("Set Active")
                  }
                }
                IconButton(onClick = { viewModel.deleteSenderProfile(profile.id) }) {
                  Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete sender profile ${profile.label}",
                  )
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
          onClick = { showAddSenderDialog = true },
          modifier = Modifier.testTag("add_sender_number_button"),
        ) {
          Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Add WhatsApp Business Number")
        }
      }

      // 2. Country Calling Code (+91 Default + On/Off Switch)
      SettingsSectionCard(
        title = "Country Calling Code & CSV Normalization",
        subtitle =
          "Numbers starting with '+' in CSV always keep their explicit country code. Toggle below to control whether numbers missing '+' automatically receive your fallback code.",
        icon = Icons.Outlined.Tune,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
              text = "Auto-apply default calling code when missing",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text =
                if (uiState.settings.autoApplyDefaultCountryCode)
                  "ON — Local CSV numbers without '+' get prefixed with ${uiState.settings.defaultCountryCode}."
                else
                  "OFF — CSV numbers must already include an explicit '+' country code or will be flagged invalid.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = uiState.settings.autoApplyDefaultCountryCode,
            onCheckedChange = viewModel::toggleAutoApplyDefaultCode,
            modifier = Modifier.testTag("toggle_auto_apply_country_code"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = callingCodeInput,
            onValueChange = { callingCodeInput = it },
            label = { Text("Default Calling Code") },
            placeholder = { Text("+91") },
            singleLine = true,
            enabled = uiState.settings.autoApplyDefaultCountryCode,
            modifier = Modifier.weight(1f).testTag("default_calling_code_input"),
          )
          Button(
            onClick = { viewModel.saveDefaultCountryCode(callingCodeInput) },
            enabled = uiState.settings.autoApplyDefaultCountryCode,
            modifier = Modifier.height(52.dp).testTag("save_calling_code_button"),
          ) {
            Text("Save Code")
          }
        }

        HorizontalDivider()

        Text("Live CSV Normalization Preview", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
          value = sampleCsvPhone,
          onValueChange = { sampleCsvPhone = it },
          label = { Text("Sample CSV phone value (try 9876543210 or +14155552671)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("sample_csv_phone_input"),
        )
        val previewResult =
          PhoneNumberHelper.normalize(
            rawInput = sampleCsvPhone,
            defaultCountryCode = uiState.settings.defaultCountryCode,
            autoApplyDefaultCode = uiState.settings.autoApplyDefaultCountryCode,
          )
        when (previewResult) {
          is PhoneNumberHelper.NormalizationResult.Valid -> {
            val sourceNote =
              if (previewResult.appliedDefaultCode)
                "Applied fallback ${uiState.settings.defaultCountryCode}"
              else "Preserved CSV country code"
            Text(
              text = "Valid E.164: ${previewResult.e164} ($sourceNote)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }
          is PhoneNumberHelper.NormalizationResult.Invalid -> {
            Text(
              text = "Flagged invalid: ${previewResult.reason}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      // 3. Google Cloud Run Backend & Android Keystore Authorization
      SettingsSectionCard(
        title = "Google Cloud Run Backend & Device Key",
        subtitle =
          "ReachHub connects to your Google Cloud Run backend over HTTPS and signs requests using a hardware-backed ECDSA P-256 key inside Android Keystore.",
        icon = Icons.Outlined.CloudQueue,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = cloudRunUrlInput,
            onValueChange = { cloudRunUrlInput = it },
            label = { Text("Google Cloud Run Base URL (HTTPS)") },
            placeholder = { Text("https://reachhub-api-xxxx.a.run.app") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("cloud_run_url_input"),
          )
          Button(
            onClick = { viewModel.saveCloudRunBaseUrl(cloudRunUrlInput) },
            modifier = Modifier.height(52.dp).testTag("save_cloud_run_url_button"),
          ) {
            Text("Save URL")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = testNumberInput,
            onValueChange = { testNumberInput = it },
            label = { Text("Operator Test Recipient WhatsApp Number (Optional)") },
            placeholder = { Text("+9198XXXXXXXX") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("operator_test_number_input"),
          )
          OutlinedButton(
            onClick = { viewModel.saveOperatorTestNumber(testNumberInput) },
            modifier = Modifier.height(52.dp).testTag("save_test_number_button"),
          ) {
            Text("Save")
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Key,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text("Device Signing Key (ECDSA P-256)", style = MaterialTheme.typography.titleSmall)
              }
              IconButton(onClick = { showRotateKeyDialog = true }) {
                Icon(
                  imageVector = Icons.Outlined.Refresh,
                  contentDescription = "Rotate device signing key",
                )
              }
            }
            if (uiState.deviceKeyStatus.isReady) {
              Text(
                text = "SHA-256 Fingerprint:\n${uiState.deviceKeyStatus.publicKeyFingerprint}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text(
                text =
                  uiState.deviceKeyStatus.errorMessage
                    ?: "Keystore status pending initialization",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = { showRegisterCloudRunDialog = true },
            enabled = !uiState.isVerifyingBackend && uiState.settings.cloudRunBaseUrl.startsWith("https://"),
            modifier = Modifier.weight(1f).testTag("register_device_cloudrun_button"),
          ) {
            Text(if (uiState.isVerifyingBackend) "Working..." else "Register Tablet Key")
          }
          OutlinedButton(
            onClick = viewModel::verifyCloudRunReadiness,
            enabled = !uiState.isVerifyingBackend && uiState.settings.cloudRunBaseUrl.startsWith("https://"),
            modifier = Modifier.weight(1f).testTag("verify_cloudrun_readiness_button"),
          ) {
            Text("Verify Readiness")
          }
        }
      }

      // 4. Campaign Processing & Retry Controls
      SettingsSectionCard(
        title = "Dispatch Batching & Retry Preferences",
        subtitle = "Tuned for controlled throughput across 2,000–20,000 recipient campaigns.",
        icon = Icons.Outlined.Security,
      ) {
        Text(
          text = "Batch size per Cloud Tasks wave: ${batchSizeSlider.roundToInt()} recipients",
          style = MaterialTheme.typography.titleSmall,
        )
        Slider(
          value = batchSizeSlider,
          onValueChange = { batchSizeSlider = it },
          valueRange = 10f..200f,
          steps = 18,
          modifier = Modifier.testTag("batch_size_slider"),
        )
        Text(
          text = "Max automatic temporary-error retries: ${maxRetriesSlider.roundToInt()}",
          style = MaterialTheme.typography.titleSmall,
        )
        Slider(
          value = maxRetriesSlider,
          onValueChange = { maxRetriesSlider = it },
          valueRange = 0f..5f,
          steps = 4,
          modifier = Modifier.testTag("max_retries_slider"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Campaign completion notifications", style = MaterialTheme.typography.bodyMedium)
          Switch(
            checked = uiState.settings.notificationsEnabled,
            onCheckedChange = viewModel::toggleNotifications,
          )
        }
        Button(
          onClick = {
            viewModel.saveBatchAndRetrySettings(
              batchSize = batchSizeSlider.roundToInt(),
              maxRetries = maxRetriesSlider.roundToInt(),
            )
          },
          modifier = Modifier.testTag("save_batch_retry_button"),
        ) {
          Text("Save Processing Preferences")
        }
      }

      // 5. 12-Point Production Readiness Gate
      val completedChecks = uiState.readinessChecklist.count { it.isVerified }
      val totalChecks = uiState.readinessChecklist.size.coerceAtLeast(1)
      SettingsSectionCard(
        title = "Production Readiness Gate ($completedChecks / $totalChecks Verified)",
        subtitle =
          "Production WhatsApp dispatch stays locked in Simulation Mode until all 12 security, backend, and cost-control checks pass.",
        icon = Icons.Outlined.Lock,
      ) {
        LinearProgressIndicator(
          progress = { completedChecks.toFloat() / totalChecks.toFloat() },
          modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (uiState.settings.simulationModeEnabled) "Simulation Mode Enabled (Safe)"
              else "Production Mode Active",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Switch(
            checked = !uiState.settings.simulationModeEnabled,
            onCheckedChange = { productionWanted ->
              viewModel.toggleSimulationMode(enabled = !productionWanted)
            },
            modifier = Modifier.testTag("production_mode_switch"),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
              text = "GCP Billing Budget Alerts & Spending Caps Confirmed",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "Confirm you have set Cloud Run and Meta Cloud API spend alerts in Google Cloud Billing.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = uiState.settings.gcpBudgetAlertConfirmed,
            onCheckedChange = viewModel::toggleGcpBudgetConfirmed,
            modifier = Modifier.testTag("gcp_budget_confirmed_switch"),
          )
        }
        HorizontalDivider()
        uiState.readinessChecklist.forEach { item ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector =
                if (item.isVerified) Icons.Outlined.CheckCircle
                else Icons.Outlined.RadioButtonUnchecked,
              contentDescription = null,
              tint =
                if (item.isVerified) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text("${item.id}. ${item.title}", style = MaterialTheme.typography.titleSmall)
              Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // 6. About & PII Privacy Notice
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("About ReachHub", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Version ${BuildConfig.VERSION_NAME} • Application ID ${BuildConfig.APPLICATION_ID}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              "Single-operator native Android WhatsApp Business Campaign Manager. Local PII is excluded from Android cloud auto-backup (allowBackup=false), and Meta API secrets are never bundled in the APK.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (showAddSenderDialog) {
    AddSenderProfileDialog(
      defaultCode = uiState.settings.defaultCountryCode,
      onDismiss = { showAddSenderDialog = false },
      onSave = { label, phone, profileRef, makeDefault ->
        viewModel.addSenderProfile(
          label = label,
          rawPhoneNumber = phone,
          cloudRunProfileRef = profileRef,
          makeDefault = makeDefault,
        )
        showAddSenderDialog = false
      },
    )
  }

  if (showRotateKeyDialog) {
    AlertDialog(
      onDismissRequest = { showRotateKeyDialog = false },
      title = { Text("Rotate Device Signing Key?") },
      text = {
        Text(
          "Rotating generates a new Android Keystore ECDSA P-256 key pair and invalidates the old public key until you re-register it on Google Cloud Run."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.rotateDeviceKey()
            showRotateKeyDialog = false
          }
        ) {
          Text("Rotate Key")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRotateKeyDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showRegisterCloudRunDialog) {
    var bootstrapToken by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showRegisterCloudRunDialog = false },
      title = { Text("Register Tablet Key on Cloud Run") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text =
              "Sends only your tablet's ECDSA P-256 public key to Cloud Run. If your Cloud Run deployment uses a one-time REACHHUB_BOOTSTRAP_TOKEN, enter it below (it is never stored on disk).",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = bootstrapToken,
            onValueChange = { bootstrapToken = it },
            label = { Text("Bootstrap Token (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("cloudrun_bootstrap_token_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.registerDeviceAndVerify(bootstrapToken.takeIf { it.isNotBlank() })
            bootstrapToken = ""
            showRegisterCloudRunDialog = false
          },
          modifier = Modifier.testTag("confirm_register_cloudrun_button"),
        ) {
          Text("Register & Verify")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRegisterCloudRunDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  content: @Composable () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Text(text = title, style = MaterialTheme.typography.titleLarge)
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      content()
    }
  }
}

@Composable
private fun AddSenderProfileDialog(
  defaultCode: String,
  onDismiss: () -> Unit,
  onSave: (label: String, phone: String, cloudRunProfileRef: String, makeDefault: Boolean) -> Unit,
) {
  var label by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var profileRef by remember { mutableStateOf("primary_waba_line") }
  var makeDefault by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add WhatsApp Business Number") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("Business Line Label") },
          placeholder = { Text("e.g., Campus Outreach Desk") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("sender_label_input"),
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("WhatsApp Business Phone Number") },
          placeholder = { Text("e.g., $defaultCode 9876543210") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("sender_phone_input"),
        )
        OutlinedTextField(
          value = profileRef,
          onValueChange = { profileRef = it },
          label = { Text("Cloud Run Sender Ref (Non-Secret)") },
          placeholder = { Text("primary_waba_line") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("sender_ref_input"),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
          Text("Set as active campaign sender", style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(label, phone, profileRef, makeDefault) },
        modifier = Modifier.testTag("confirm_add_sender_button"),
      ) {
        Text("Save Number")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
