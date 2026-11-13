package dev.pulse.app.ui.sources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.data.db.StoredDataSummary
import dev.pulse.app.data.importer.AppleHealthImportManager
import dev.pulse.app.data.importer.ImportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppleImportViewModel @Inject constructor(
    private val manager: AppleHealthImportManager,
) : ViewModel() {

    val importState: StateFlow<ImportState> = manager.state

    private val _stored = MutableStateFlow<StoredDataSummary?>(null)
    /** Что уже лежит в базе из Apple Health; null — ещё не посчитали. */
    val stored: StateFlow<StoredDataSummary?> = _stored.asStateFlow()

    init {
        // Пересчитываем сводку при старте и каждый раз, когда импорт закончился (успехом или нет).
        viewModelScope.launch {
            manager.state.collect { state ->
                if (state !is ImportState.Running) _stored.value = manager.storedSummary()
            }
        }
    }

    fun start(uri: Uri) = manager.start(uri)
    fun cancel() = manager.cancel()
}
