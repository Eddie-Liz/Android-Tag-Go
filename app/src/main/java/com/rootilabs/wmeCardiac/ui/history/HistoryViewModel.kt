package com.rootilabs.wmeCardiac.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootilabs.wmeCardiac.data.local.EventTagDbEntity
import com.rootilabs.wmeCardiac.di.ServiceLocator
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    companion object {
        private const val UPLOAD_FAILED = "UPLOAD_FAILED"
    }

    private val repository get() = ServiceLocator.repository

    var tags by mutableStateOf<List<EventTagDbEntity>>(emptyList())
        private set

    var isSyncing by mutableStateOf(false)
        private set

    var uploadError by mutableStateOf<String?>(null)
        private set

    init {
        loadTags()
    }

    fun loadTags() {
        viewModelScope.launch {
            tags = repository.getLocalEventTags()
        }
    }

    fun retryUnsyncedTags() {
        viewModelScope.launch {
            val unsynced = tags.filter { it.isEdit }
            if (unsynced.isEmpty()) return@launch

            uploadError = null
            try {
                isSyncing = true
                val result = repository.uploadVirtualEventTags(unsynced)
                if (result.isSuccess) {
                    uploadError = null
                } else {
                    // Raw exception text (server English, host names, stack messages) must not reach
                    // the UI; HistoryScreen maps known codes and falls back to a localized message.
                    uploadError = result.exceptionOrNull()?.message ?: UPLOAD_FAILED
                }
            } catch (e: Exception) {
                uploadError = e.message ?: UPLOAD_FAILED
            } finally {
                isSyncing = false
                loadTags() // Always reload to reflect actual DB state
            }
        }
    }
}
