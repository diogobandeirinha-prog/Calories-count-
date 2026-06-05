package com.caloriescount.app.data.repository

import com.caloriescount.app.data.db.FoodEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Verifies day/week/month bucketing and the today / this-week / this-month roll-ups.
 *
 * [NutritionStats.compute] takes injectable zone/today/locale, so these run deterministically:
 *   zone   = UTC (no DST shifts at the noon timestamps used below)
 *   locale = US (week starts Sunday)
 *   today  = Sat 2024-06-15 → week W24 (Sun 06-09 … Sat 06-15), month June
 */
class NutritionStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val locale: Locale = Locale.US
    private val today: LocalDate = LocalDate.of(2024, 6, 15)

    private fun entry(
        date: LocalDate,
        cal: Double = 100.0,
        protein: Double = 10.0,
        carbs: Double = 20.0,
        fats: Double = 5.0
    ): FoodEntryEntity = FoodEntryEntity(
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        mealName = "meal",
        totalCalories = cal,
        totalProteinG = protein,
        totalCarbsG = carbs,
        totalFatsG = fats,
        items = emptyList()
    )

    private fun compute(vararg entries: FoodEntryEntity): StatsSnapshot =
        NutritionStats.compute(entries.toList(), zone, today, locale)

    @Test
    fun empty_yieldsZeroTotalsAndNoBuckets() {
        val s = compute()
        assertEquals(0.0, s.today.calories, 0.0)
        assertEquals(0, s.today.entryCount)
        assertEquals(0, s.thisWeek.entryCount)
        assertEquals(0, s.thisMonth.entryCount)
        assertTrue(s.byDay.isEmpty())
        assertTrue(s.byWeek.isEmpty())
        assertTrue(s.byMonth.isEmpty())
    }

    @Test
    fun sameDayEntries_sumAcrossAllMacros() {
        val s = compute(
            entry(today, cal = 100.0, protein = 10.0, carbs = 20.0, fats = 5.0),
            entry(today, cal = 200.0, protein = 15.0, carbs = 30.0, fats = 8.0)
        )
        assertEquals(300.0, s.today.calories, 0.0)
        assertEquals(25.0, s.today.protein, 0.0)
        assertEquals(50.0, s.today.carbs, 0.0)
        assertEquals(13.0, s.today.fats, 0.0)
        assertEquals(2, s.today.entryCount)

        assertEquals(1, s.byDay.size)
        assertEquals("Today", s.byDay[0].label)
        assertEquals(2, s.byDay[0].totals.entryCount)
        assertEquals(today.toString(), s.byDay[0].key)
    }

    @Test
    fun byDay_isNewestFirst_withTodayYesterdayLabels() {
        val s = compute(entry(today), entry(today.minusDays(1)))
        assertEquals(2, s.byDay.size)
        assertEquals("Today", s.byDay[0].label)
        assertEquals("Yesterday", s.byDay[1].label)
        // "today" roll-up excludes yesterday's entry.
        assertEquals(1, s.today.entryCount)
        assertEquals(100.0, s.today.calories, 0.0)
    }

    @Test
    fun thisWeek_includesStartOfWeekThroughToday_excludesOutside() {
        val s = compute(
            entry(LocalDate.of(2024, 6, 9)),  // Sun, start of this week — included
            entry(LocalDate.of(2024, 6, 8)),  // prev Sat — excluded
            entry(LocalDate.of(2024, 6, 16)), // tomorrow (future) — excluded
            entry(today)                      // included
        )
        assertEquals(2, s.thisWeek.entryCount)
        assertEquals(200.0, s.thisWeek.calories, 0.0)
    }

    @Test
    fun thisMonth_includesFirstOfMonthThroughToday_excludesOutside() {
        val s = compute(
            entry(LocalDate.of(2024, 5, 31)), // last day of May — excluded
            entry(LocalDate.of(2024, 6, 1)),  // first of June — included
            entry(LocalDate.of(2024, 6, 20)), // future — excluded
            entry(today)                      // included
        )
        assertEquals(2, s.thisMonth.entryCount)
        assertEquals(200.0, s.thisMonth.calories, 0.0)
    }

    @Test
    fun byMonth_groupsAndSortsNewestFirst() {
        val s = compute(
            entry(LocalDate.of(2024, 5, 10)),
            entry(LocalDate.of(2024, 6, 5)),
            entry(today) // 2024-06-15
        )
        assertEquals(2, s.byMonth.size)
        assertEquals("2024-06-01", s.byMonth[0].key) // June first (newest)
        assertEquals("2024-05-01", s.byMonth[1].key)
        assertEquals(2, s.byMonth[0].totals.entryCount) // 06-05 + 06-15
        assertEquals(1, s.byMonth[1].totals.entryCount)
        assertTrue(s.byMonth[0].label.contains("June"))
        assertTrue(s.byMonth[0].label.contains("2024"))
    }

    @Test
    fun byWeek_splitsAcrossWeekBoundary() {
        val s = compute(
            entry(LocalDate.of(2024, 6, 8)), // W23
            entry(LocalDate.of(2024, 6, 9)), // W24
            entry(today)                     // W24
        )
        assertEquals(2, s.byWeek.size)
        // Every entry lands in exactly one weekly bucket.
        assertEquals(3, s.byWeek.sumOf { it.totals.entryCount })
        // Current week (W24) sorts first and holds two entries.
        assertEquals(2, s.byWeek[0].totals.entryCount)
        assertEquals(1, s.byWeek[1].totals.entryCount)
    }

    @Test
    fun entriesAreReturnedNewestFirstWithinABucket() {
        val morning = entry(today).copy(
            timestamp = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
            mealName = "breakfast"
        )
        val evening = entry(today).copy(
            timestamp = today.atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
            mealName = "dinner"
        )
        val s = compute(morning, evening)
        val todayBucket = s.byDay.first { it.key == today.toString() }
        assertEquals("dinner", todayBucket.entries[0].mealName) // latest first
        assertEquals("breakfast", todayBucket.entries[1].mealName)
    }
}
