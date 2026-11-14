package dev.pulse.app.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.data.db.DataOrigin
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.db.StoredDataSummary
import dev.pulse.app.data.health.DataAvailability
import dev.pulse.app.data.health.HealthConnectRepository
import dev.pulse.app.data.health.HealthConnectStatus
import dev.pulse.app.data.health.HealthConnectSyncer
import dev.pulse.app.data.health.SyncState
import dev.pulse.app.data.settings.ThemeSettings
import dev.pulse.app.notify.RecoveryNotifier
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
    /** Умеет ли телефон отдавать данные, когда приложение закрыто, и разрешено ли это. */
    val backgroundSupported: Boolean = false,
    val backgroundGranted: Boolean = false,
    val sync: SyncState = SyncState(),
    /** Что уже лежит в базе Pulse из Health Connect. */
    val stored: StoredDataSummary? = null,
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
    private val syncer: HealthConnectSyncer,
    private val dao: HealthDao,
    private val themeSettings: ThemeSettings,
    private val notifier: RecoveryNotifier,
) : ViewModel() {

    fun toggleTheme() = themeSettings.toggle()

    val notificationsEnabled get() = notifier.enabled
    fun setNotifications(enabled: Boolean) = notifier.setEnabled(enabled)


    private val _state = MutableStateFlow(DataSourcesUiState())
    val state: StateFlow<DataSourcesUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            syncer.loadState()
            syncer.state.collect { sync ->
                // После каждой синхронизации пересчитываем, что реально попало в базу.
                val stored = if (sync.running) _state.value.stored else dao.summary(DataOrigin.HEALTH_CONNECT)
                _state.update { it.copy(sync = sync, stored = stored) }
            }
        }
    }

    val permissionContract get() = repository.permissionRequestContract()

    /** Разрешения, которые нужно запросить: все поддерживаемые телефоном виды данных. */
    fun permissionsToRequest(): Set<String> =
        repository.supportedTypes().map(repository::permissionFor).toSet() +
            listOfNotNull(repository.backgroundReadPermission())

    /** Запустить синхронизацию прямо сейчас (кнопка на экране). */
    fun syncNow() {
        syncer.requestSync(force = true)
    }

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
            val backgroundPermission = repository.backgroundReadPermission()
            val backgroundGranted = backgroundPermission != null &&
                backgroundPermission in repository.grantedPermissions()
            _state.update {
                it.copy(
                    loading = false,
                    items = items,
                    backgroundSupported = backgroundPermission != null,
                    backgroundGranted = backgroundGranted,
                )
            }
            // Заодно подтягиваем свежие данные в базу, если давно этого не делали.
            if (items.any { it.granted }) syncer.requestSync(force = false)
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        _state.update { it.copy(requestWasDenied = granted.isEmpty() && !it.anyGranted) }
        refresh()
        // Только что выдали доступ — сразу загружаем данные, не дожидаясь расписания.
        if (granted.isNotEmpty()) syncNow()
    }
}
