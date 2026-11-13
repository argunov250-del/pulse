package dev.pulse.app.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.data.health.DataAvailability
import dev.pulse.app.data.health.HealthConnectRepository
import dev.pulse.app.data.health.HealthConnectStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataSourcesUiState(
    /** null — ещё не проверяли. */
    val status: HealthConnectStatus? = null,
    val loading: Boolean = true,
    val items: List<DataAvailability> = emptyList(),
    /** Пользователь нажал «Разрешить», но ничего не выдал — подсказываем открыть Health Connect. */
    val requestWasDenied: Boolean = false,
) {
    private val supported get() = items.filter { it.supported }
    val grantedCount: Int get() = supported.count { it.granted }
    val supportedCount: Int get() = supported.size
    val anyGranted: Boolean get() = grantedCount > 0
    val allGranted: Boolean get() = supportedCount > 0 && grantedCount == supportedCount
    /** Health Connect есть, но доступа нет вовсе — показываем приглашение подключить. */
    val needsConnection: Boolean get() = !loading && (status != HealthConnectStatus.AVAILABLE || !anyGranted)
}

@HiltViewModel
class DataSourcesViewModel @Inject constructor(
    private val repository: HealthConnectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DataSourcesUiState())
    val state: StateFlow<DataSourcesUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    val permissionContract get() = repository.permissionRequestContract()

    /** Разрешения, которые нужно запросить: все поддерживаемые телефоном виды данных. */
    fun permissionsToRequest(): Set<String> =
        repository.supportedTypes().map(repository::permissionFor).toSet()

    fun manageIntent() = repository.manageIntent()
    fun installIntent() = repository.installIntent()

    /** Перечитать состояние: вызывается при открытии экрана и при возврате в приложение. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val status = repository.status()
            if (status != HealthConnectStatus.AVAILABLE) {
                _state.update { it.copy(status = status, loading = false, items = emptyList()) }
                return@launch
            }
            _state.update { it.copy(status = status, loading = it.items.isEmpty()) }
            val items = repository.loadAvailability()
            _state.update { it.copy(loading = false, items = items) }
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        _state.update { it.copy(requestWasDenied = granted.isEmpty() && !it.anyGranted) }
        refresh()
    }
}
