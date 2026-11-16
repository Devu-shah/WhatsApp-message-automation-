package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ReachHubApplication
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that orchestrates batched campaign dispatch
 * (Simulation Sandbox or Cloud Run Production) and survives temporary UI pauses.
 */
class CampaignExecutionWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val campaignId = inputData.getLong(KEY_CAMPAIGN_ID, -1L)
    if (campaignId <= 0L) return Result.failure()

    val app = applicationContext as? ReachHubApplication ?: return Result.failure()
    return app.container.campaignRepository
      .executeCampaignDispatchLoop(campaignId)
      .fold(
        onSuccess = { Result.success() },
        onFailure = { error ->
          if (runAttemptCount < 3) Result.retry()
          else Result.failure(Data.Builder().putString(KEY_ERROR, error.message).build())
        },
      )
  }

  companion object {
    const val KEY_CAMPAIGN_ID = "campaign_id"
    const val KEY_ERROR = "error_message"

    fun uniqueWorkName(campaignId: Long): String = "reachhub_campaign_dispatch_$campaignId"

    fun enqueue(context: Context, campaignId: Long, initialDelayMs: Long = 0L) {
      val data = Data.Builder().putLong(KEY_CAMPAIGN_ID, campaignId).build()
      val builder = OneTimeWorkRequestBuilder<CampaignExecutionWorker>().setInputData(data)
      if (initialDelayMs > 0L) {
        builder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
      }
      WorkManager.getInstance(context)
        .enqueueUniqueWork(
          uniqueWorkName(campaignId),
          ExistingWorkPolicy.REPLACE,
          builder.build(),
        )
    }

    fun cancel(context: Context, campaignId: Long) {
      WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(campaignId))
    }
  }
}
