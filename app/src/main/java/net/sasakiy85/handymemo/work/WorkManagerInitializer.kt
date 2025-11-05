package net.sasakiy85.handymemo.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerInitializer {
    private const val TAG = "WorkManagerInitializer"

    fun initializeWorkManager(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 初回起動時の OneTimeWork を登録（短い遅延で実行）
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<MemoIndexerWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS) // 5秒後に実行
            .build()

        workManager.enqueueUniqueWork(
            MemoIndexerWorker.WORK_NAME_ONETIME,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )

        Log.d(TAG, "OneTimeWork enqueued: ${MemoIndexerWorker.WORK_NAME_ONETIME}")

        // 15分間隔の PeriodicWork を登録
        val constraints = Constraints.Builder()
            .setRequiresCharging(false) // 充電不要（任意で変更可能）
            .setRequiresBatteryNotLow(false) // 低電力モードでも実行（任意で変更可能）
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // ネットワーク不要
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<MemoIndexerWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            MemoIndexerWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP, // 既存のWorkがあれば保持
            periodicWorkRequest
        )

        Log.d(TAG, "PeriodicWork enqueued: ${MemoIndexerWorker.WORK_NAME_PERIODIC}")
    }
}

