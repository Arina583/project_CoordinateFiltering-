import kotlin.math.*

fun main() {
    val provider = MockGpsProvider(seed = 42)
    val filter = LocationFilter(minAccuracyM = 15.0, minIntervalMs = 3_000)
    val averager = Averager(window = 3)

    val rawErrors = mutableListOf<Double>()
    val smoothErrors = mutableListOf<Double>()
    val lat0 = 56.1290; val lon0 = 40.4070
    val stepLat = 2.0 / 111_320.0

    for (t in 0 until 40) {
        val sample = provider.next()
        val trueLat = lat0 + stepLat * t; val trueLon = lon0
        val accepted = filter.accept(sample)

        // Состояние «нет сигнала»: держим последнюю известную точку
        if (sample == null) {
            println("тик $t: сигнал отсутствует (подвал), " +
                    "показана последняя известная точка")
            continue
        }
        if (accepted == null) {
            println("тик $t: точка отброшена " +
                    "(accuracy=${"%.0f".format(sample.accuracy)} м)")
            continue
        }

        // Усреднение окном 3
        val (avgLat, avgLon) = averager.push(accepted)

        // Ошибка относительно истинного положения
        rawErrors += distanceM(sample.lat, sample.lon, trueLat, trueLon)
        smoothErrors += distanceM(avgLat, avgLon, trueLat, trueLon)
    }

    // Метрики: 2-D RMS по горизонтальной плоскости
    fun rms(xs: List<Double>) = sqrt(xs.map { it * it }.average())
    println("Этап 1-2. Принято точек: ${rawErrors.size}, " +
            "отброшено по точности: ${filter.rejectedAccuracy}, " +
            "по интервалу: ${filter.rejectedInterval}")
    println("Этап 3. RMS сырых точек:          ${"%.1f".format(rms(rawErrors))} м")
    println("Этап 3. RMS сглаженных (окно 3):  ${"%.1f".format(rms(smoothErrors))} м")
    // Ожидание: сглаженный RMS примерно в 1,5–1,8 раза меньше сырого
}