package sanctuary.app.feature.dump.presentation.utils

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object TimeUtils {

    @OptIn(ExperimentalTime::class)
    fun currentEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    fun Long.toTimerText(): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    fun formatEpochMs(epochMs: Long): String {
        val diffMs = currentEpochMs() - epochMs
        return when {
            diffMs < 60_000L -> "just now"
            diffMs < 3_600_000L -> "${diffMs / 60_000L}m ago"
            diffMs < 86_400_000L -> "${diffMs / 3_600_000L}h ago"
            diffMs < 7 * 86_400_000L -> "${diffMs / 86_400_000L}d ago"
            else -> {
                val days = epochMs / 86_400_000L
                val year = epochYear(days)
                val dayOfYear = (days - daysToYear(year)).toInt()
                val month = monthFromDayOfYear(dayOfYear, isLeapYear(year))
                val day = dayOfYear - daysToMonth(month - 1, isLeapYear(year)) + 1
                val monthName = MONTH_NAMES[month - 1]
                "$monthName $day, $year"
            }
        }
    }

    private fun epochYear(totalDays: Long): Int {
        var year = 1970
        var days = totalDays
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366 else 365
            if (days < daysInYear) break
            days -= daysInYear
            year++
        }
        return year
    }

    private fun daysToYear(year: Int): Long {
        val y = (year - 1).toLong()
        return y * 365 + y / 4 - y / 100 + y / 400 - (1969L * 365 + 1969 / 4 - 1969 / 100 + 1969 / 400)
    }

    private fun isLeapYear(year: Int) = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun daysToMonth(month: Int, leap: Boolean): Int {
        val days = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        return days[month] + if (leap && month >= 2) 1 else 0
    }

    private fun monthFromDayOfYear(dayOfYear: Int, leap: Boolean): Int {
        for (m in 12 downTo 1) {
            if (dayOfYear >= daysToMonth(m - 1, leap)) return m
        }
        return 1
    }

    private val MONTH_NAMES = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
}
