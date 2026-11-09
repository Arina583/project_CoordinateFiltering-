import kotlin.random.Random

/** Одна выборка приёмника. accuracy — радиус 68%-го круга, метры. */
data class GpsSample(
    val lat: Double, val lon: Double,
    val accuracy: Double, val timestamp: Long
)

/** Сцены движения: открытое небо, городское ущелье, подвал. */
enum class Scene { OPEN, CANYON, BASEMENT }

/**
 * Имитатор GNSS-приёмника. Истинная позиция движется по прямой
 * (к северу) с шагом 2 м за тик; шум зависит от сцены;
 * в CANYON точность в 4–8 раз хуже, в BASEMENT сигнал пропадает (null).
 */
class MockGpsProvider(seed: Long = 42) {
    private val rng = Random(seed)
    private var tick = 0
    private val lat0 = 56.1290      // старт: район центра Владимира
    private val lon0 = 40.4070
    private val stepLat = 2.0 / 111_320.0   // 2 м к северу за тик

    /** Возвращает выборку на текущем тике или null — сигнала нет. */
    fun next(): GpsSample? {
        val scene = when {
            tick in 0..9 -> Scene.OPEN          // этап 1: открытое небо
            tick in 10..19 -> Scene.CANYON      // этап 2: городское ущелье
            tick in 20..24 -> Scene.BASEMENT    // этап 3: подвал, сигнала нет
            else -> Scene.OPEN                  // этап 4: сигнал вернулся
        }
        val t = tick
        tick++
        if (scene == Scene.BASEMENT) return null
        val trueLat = lat0 + stepLat * t
        val trueLon = lon0
        val sigma = if (scene == Scene.OPEN) 3.0 else 20.0   // шум, м
        val accuracy = if (scene == Scene.OPEN)
            5.0 + rng.nextDouble() * 3.0        // 5–8 м
        else
            25.0 + rng.nextDouble() * 35.0      // 25–60 м
        val jitterLat = sigma / 111_320.0 * rng.nextDouble(-2.0, 2.0)
        val jitterLon = sigma / (111_320.0 * 0.6) * rng.nextDouble(-2.0, 2.0)
        return GpsSample(trueLat + jitterLat, trueLon + jitterLon,
            accuracy, t * 1_000L)
    }
}

/** Фильтр: отбраковка по точности и по интервалу обновления. */
class LocationFilter(
    private val minAccuracyM: Double = 15.0,   // порог точности, м
    private val minIntervalMs: Long = 3_000    // не чаще одного раза в 3 с
) {
    private var lastAcceptedAt = -1L
    var rejectedAccuracy = 0; private set
    var rejectedInterval = 0; private set

    /** Возвращает выборку для обработки либо null — точка отброшена. */
    fun accept(s: GpsSample?): GpsSample? {
        if (s == null) return null                       // сигнала нет
        if (s.accuracy > minAccuracyM) { rejectedAccuracy++; return null }
        if (s.timestamp - lastAcceptedAt < minIntervalMs) {
            rejectedInterval++; return null
        }
        lastAcceptedAt = s.timestamp
        return s
    }
}

/** Усреднение последних N (по умолчанию 3) принятых точек. */
class Averager(private val window: Int = 3) {
    private val buf = ArrayDeque<GpsSample>()

    fun push(s: GpsSample): Pair<Double, Double> {
        buf.addLast(s)
        while (buf.size > window) buf.removeFirst()
        val lat = buf.map { it.lat }.average()
        val lon = buf.map { it.lon }.average()
        return lat to lon
    }
}

/** Расстояние между точками в метрах (упрощённая равнопромежуточная формула). */
fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1) * 111_320.0
    val dLon = (lon2 - lon1) * 111_320.0 * 0.6
    return Math.sqrt(dLat * dLat + dLon * dLon)
}
