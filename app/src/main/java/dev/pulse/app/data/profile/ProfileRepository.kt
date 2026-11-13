package dev.pulse.app.data.profile

import dev.pulse.app.algorithms.engine.Profile
import dev.pulse.app.data.db.AppMetaEntity
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.importer.AppleHealthImportManager
import dev.pulse.app.data.importer.MeasurementType
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

/** Профиль человека в том виде, в каком он хранится и редактируется. */
data class UserProfile(
    /** Дата рождения; null — неизвестна. */
    val dateOfBirth: LocalDate? = null,
    val female: Boolean = false,
    /** true — пол указан (вручную или в архиве Apple Health). */
    val sexKnown: Boolean = false,
    val heightCm: Double? = null,
    /** Максимальный пульс, заданный вручную; null — считать по возрасту и данным. */
    val hrMaxOverride: Double? = null,
) {
    val ageYears: Double?
        get() = dateOfBirth?.let {
            val period = Period.between(it, LocalDate.now())
            period.years + period.months / 12.0
        }?.takeIf { it in 10.0..110.0 }

    fun toEngineProfile() = Profile(
        ageYears = ageYears,
        female = female,
        hrMaxOverride = hrMaxOverride,
        heightCm = heightCm,
    )
}

/**
 * Хранит профиль в базе на телефоне. Дата рождения, пол и рост сначала берутся
 * из архива Apple Health, потом их можно поправить вручную.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: HealthDao,
) {
    suspend fun load(): UserProfile {
        val sex = dao.getMeta(AppleHealthImportManager.META_SEX)
        return UserProfile(
            dateOfBirth = dao.getMeta(AppleHealthImportManager.META_DATE_OF_BIRTH)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            female = sex == SEX_FEMALE,
            sexKnown = sex == SEX_FEMALE || sex == SEX_MALE,
            heightCm = dao.getMeta(META_HEIGHT)?.toDoubleOrNull()
                ?: dao.latestMeasurement(MeasurementType.HEIGHT_CM.ordinal)?.value,
            hrMaxOverride = dao.getMeta(META_HR_MAX)?.toDoubleOrNull(),
        )
    }

    suspend fun save(profile: UserProfile) {
        put(AppleHealthImportManager.META_DATE_OF_BIRTH, profile.dateOfBirth?.toString())
        put(AppleHealthImportManager.META_SEX, if (profile.female) SEX_FEMALE else SEX_MALE)
        put(META_HEIGHT, profile.heightCm?.toString())
        put(META_HR_MAX, profile.hrMaxOverride?.toString())
    }

    private suspend fun put(key: String, value: String?) {
        if (value == null) dao.deleteMeta(key) else dao.saveMeta(AppMetaEntity(key, value))
    }

    private companion object {
        const val META_HEIGHT = "profile.heightCm"
        const val META_HR_MAX = "profile.hrMax"
        const val SEX_FEMALE = "Female"
        const val SEX_MALE = "Male"
    }
}
