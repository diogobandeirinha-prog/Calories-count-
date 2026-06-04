package com.caloriescount.app.data.repository

import com.caloriescount.app.data.db.FoodEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/** Aggregated calories + macronutrients over a set of entries. */
data class Totals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val entryCount: Int = 0
)

/** A bucket (a day, an ISO week, or a month) with its totals and member entries. */
data class PeriodSummary(
    val key: String,
    val label: String,
    val totals: Totals,
    val entries: List<FoodEntryEntity>
)

/** Everything the stats UI needs, computed in the device's time zone. */
data class StatsSnapshot(
    val today: Totals,
    val thisWeek: Totals,
    val thisMonth: Totals,
    val byDay: List<PeriodSummary>,
    val byWeek: List<PeriodSummary>,
    val byMonth: List<PeriodSummary>
)

object NutritionStats {

    fun compute(
        entries: List<FoodEntryEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        locale: Locale = Locale.getDefault()
    ): StatsSnapshot {
        val weekFields = WeekFields.of(locale)

        fun dateOf(entry: FoodEntryEntity): LocalDate =
            Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()

        val dayGroups = entries.groupBy { dateOf(it) }
        val weekGroups = entries.groupBy {
            val d = dateOf(it)
            val week = d.get(weekFields.weekOfWeekBasedYear())
            val year = d.get(weekFields.weekBasedYear())
            "%04d-W%02d".format(year, week)
        }
        val monthGroups = entries.groupBy { dateOf(it).withDayOfMonth(1) }

        val byDay = dayGroups.entries
            .sortedByDescending { it.key }
            .map { (date, list) ->
                PeriodSummary(
                    key = date.toString(),
                    label = formatDay(date, today),
                    totals = list.toTotals(),
                    entries = list.sortedByDescending { it.timestamp }
                )
            }

        val byWeek = weekGroups.entries
            .sortedByDescending { it.key }
            .map { (key, list) ->
                val anchor = list.minByOrNull { it.timestamp }?.let { dateOf(it) } ?: today
                val start = anchor.with(weekFields.dayOfWeek(), 1)
                val end = start.plusDays(6)
                PeriodSummary(
                    key = key,
                    label = "${formatShort(start)} – ${formatShort(end)}",
                    totals = list.toTotals(),
                    entries = list.sortedByDescending { it.timestamp }
                )
            }

        val byMonth = monthGroups.entries
            .sortedByDescending { it.key }
            .map { (month, list) ->
                PeriodSummary(
                    key = month.toString(),
                    label = month.month.getDisplayName(
                        java.time.format.TextStyle.FULL, locale
                    ).replaceFirstChar { it.uppercase() } + " " + month.year,
                    totals = list.toTotals(),
                    entries = list.sortedByDescending { it.timestamp }
                )
            }

        val startOfWeek = today.with(weekFields.dayOfWeek(), 1)
        val startOfMonth = today.withDayOfMonth(1)

        return StatsSnapshot(
            today = entries.filter { dateOf(it) == today }.toTotals(),
            thisWeek = entries.filter { !dateOf(it).isBefore(startOfWeek) && !dateOf(it).isAfter(today) }.toTotals(),
            thisMonth = entries.filter { !dateOf(it).isBefore(startOfMonth) && !dateOf(it).isAfter(today) }.toTotals(),
            byDay = byDay,
            byWeek = byWeek,
            byMonth = byMonth
        )
    }

    private fun List<FoodEntryEntity>.toTotals() = Totals(
        calories = sumOf { it.totalCalories },
        protein = sumOf { it.totalProteinG },
        carbs = sumOf { it.totalCarbsG },
        fats = sumOf { it.totalFatsG },
        entryCount = size
    )

    private fun formatDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
            "$dow, ${formatShort(date)}"
        }
    }

    private fun formatShort(date: LocalDate): String {
        val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        return "$month ${date.dayOfMonth}"
    }
}
