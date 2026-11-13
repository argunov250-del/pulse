package dev.pulse.app.data.db

/** Добавляет измерения пульса к минуте: если минута уже есть — складывает, иначе создаёт. */
fun HealthDao.addHeartRate(item: HeartRateMinuteEntity) {
    val updated = updateHeartRate(
        item.minute, item.origin, item.bpmSum, item.sampleCount, item.bpmMin, item.bpmMax,
    )
    if (updated == 0) insertHeartRate(item)
}

/** Добавляет значение к сумме за час: если час уже есть — складывает, иначе создаёт. */
fun HealthDao.addHourlyTotal(item: HourlyTotalEntity) {
    val updated = updateHourlyTotal(item.type, item.hour, item.origin, item.source, item.total)
    if (updated == 0) insertHourlyTotal(item)
}
