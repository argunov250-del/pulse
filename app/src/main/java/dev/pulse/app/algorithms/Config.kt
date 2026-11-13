package dev.pulse.app.algorithms

/**
 * Все настраиваемые коэффициенты алгоритмов — в одном месте.
 * Здесь не должно быть зависимостей от Android: только чистый Kotlin.
 * Пока тут лишь то, что нужно интерфейсу; формулы появятся в фазе 4.
 */
object Config {
    /** Recovery от этого значения и выше — зелёная зона. */
    const val RECOVERY_GREEN_FROM = 67

    /** Recovery от этого значения и до зелёной зоны — жёлтая; ниже — красная. */
    const val RECOVERY_YELLOW_FROM = 34

    /** Максимум шкалы Strain. */
    const val STRAIN_MAX = 21f

    /** Целевой Strain на день в зависимости от зоны Recovery. */
    val STRAIN_TARGET_GREEN = 14f..18f
    val STRAIN_TARGET_YELLOW = 10f..14f
    val STRAIN_TARGET_RED = 0f..10f
}

enum class RecoveryZone { GREEN, YELLOW, RED }

fun recoveryZone(recoveryPercent: Int): RecoveryZone = when {
    recoveryPercent >= Config.RECOVERY_GREEN_FROM -> RecoveryZone.GREEN
    recoveryPercent >= Config.RECOVERY_YELLOW_FROM -> RecoveryZone.YELLOW
    else -> RecoveryZone.RED
}

fun strainTarget(zone: RecoveryZone): ClosedFloatingPointRange<Float> = when (zone) {
    RecoveryZone.GREEN -> Config.STRAIN_TARGET_GREEN
    RecoveryZone.YELLOW -> Config.STRAIN_TARGET_YELLOW
    RecoveryZone.RED -> Config.STRAIN_TARGET_RED
}
