package dev.pulse.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.Config
import dev.pulse.app.algorithms.StrainCalculator
import dev.pulse.app.data.metrics.MetricsRepository
import dev.pulse.app.data.profile.ProfileRepository
import dev.pulse.app.data.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Поля формы профиля — как текст, который человек набирает. */
data class ProfileFormState(
    val loaded: Boolean = false,
    val birthYear: String = "",
    val female: Boolean = false,
    val heightCm: String = "",
    val hrMax: String = "",
    /** Максимальный пульс, который будет использован, если поле пустое. */
    val autoHrMax: Int? = null,
    val birthYearError: Boolean = false,
    val heightError: Boolean = false,
    val hrMaxError: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val metrics: MetricsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state.asStateFlow()

    /** Исходная дата рождения: если год не меняли, сохраняем её целиком (с месяцем и днём). */
    private var originalBirthDate: LocalDate? = null

    init {
        viewModelScope.launch {
            val profile = profiles.load()
            originalBirthDate = profile.dateOfBirth
            _state.value = ProfileFormState(
                loaded = true,
                birthYear = profile.dateOfBirth?.year?.toString().orEmpty(),
                female = profile.female,
                heightCm = profile.heightCm?.let { formatNumber(it) }.orEmpty(),
                hrMax = profile.hrMaxOverride?.let { formatNumber(it) }.orEmpty(),
                autoHrMax = StrainCalculator.hrMaxForAge((profile.ageYears ?: Config.DEFAULT_AGE.toDouble()).toInt()).toInt(),
            )
        }
    }

    fun onBirthYear(text: String) = _state.update { it.copy(birthYear = text.digits(4), birthYearError = false, saved = false) }
    fun onFemale(female: Boolean) = _state.update { it.copy(female = female, saved = false) }
    fun onHeight(text: String) = _state.update { it.copy(heightCm = text.digits(3), heightError = false, saved = false) }
    fun onHrMax(text: String) = _state.update { it.copy(hrMax = text.digits(3), hrMaxError = false, saved = false) }

    fun save() {
        val form = _state.value
        val thisYear = LocalDate.now().year
        val year = form.birthYear.toIntOrNull()
        val height = form.heightCm.toDoubleOrNull()
        val hrMax = form.hrMax.toDoubleOrNull()
        // Пустое поле — «не указано», это не ошибка. Ошибка — неправдоподобное значение.
        val yearError = form.birthYear.isNotEmpty() && (year == null || year !in (thisYear - 110)..(thisYear - 10))
        val heightError = form.heightCm.isNotEmpty() && (height == null || height !in 100.0..250.0)
        val hrMaxError = form.hrMax.isNotEmpty() && (hrMax == null || hrMax !in 120.0..230.0)
        if (yearError || heightError || hrMaxError) {
            _state.update { it.copy(birthYearError = yearError, heightError = heightError, hrMaxError = hrMaxError) }
            return
        }
        viewModelScope.launch {
            val birthDate = when {
                year == null -> null
                originalBirthDate?.year == year -> originalBirthDate
                else -> LocalDate.of(year, 7, 1) // середина года: точнее мы не знаем
            }
            profiles.save(
                UserProfile(
                    dateOfBirth = birthDate,
                    female = form.female,
                    sexKnown = true,
                    heightCm = height,
                    hrMaxOverride = hrMax,
                ),
            )
            originalBirthDate = birthDate
            metrics.refresh() // показатели зависят от профиля — пересчитываем
            _state.update { it.copy(saved = true) }
        }
    }

    private fun String.digits(max: Int) = filter(Char::isDigit).take(max)

    private fun formatNumber(value: Double): String = value.toInt().toString()
}
