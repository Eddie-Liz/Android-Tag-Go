package com.rootilabs.wmeCardiac.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.rootilabs.wmeCardiac.data.repository.RootiCareRepository
import com.rootilabs.wmeCardiac.di.ServiceLocator

class LogoutWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val institutionId = inputData.getString("institutionId") ?: return Result.failure()
        val patientId = inputData.getString("patientId") ?: return Result.failure()
        val measureId = inputData.getString("measureId") ?: return Result.failure()

        Log.d("LogoutWorker", "Background Unsubscribe starting for: $institutionId / $patientId / $measureId")
        
        val repository = ServiceLocator.repository
        val result = repository.unsubscribePatient(institutionId, patientId, measureId)

        return if (result.isSuccess) {
            Log.d("LogoutWorker", "Background Unsubscribe SUCCESS")
            Result.success()
        } else {
            Log.w("LogoutWorker", "Background Unsubscribe FAILED, retrying...")
            Result.retry()
        }
    }
}
