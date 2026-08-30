package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private var nextId = 1L

    private fun item(taken: Long, video: Boolean = false) =
        testItem(id = nextId++, isVideo = video, taken = taken)

    private fun headers(rows: List<TimelineRow>) = rows.filterIsInstance<TimelineRow.Header>()
    private fun photoRows(rows: List<TimelineRow>) = rows.filterIsInstance<TimelineRow.Photos>()

    @Test
    fun `an empty library produces no rows`() {
        assertTrue(buildTimeline(emptyList(), ZoomLevel.LARGE, zone).isEmpty())
    }

    @Test
    fun `days become separate sections`() {
        val rows = buildTimeline(
            listOf(
                item(at(2026, 8, 30)),
                item(at(2026, 8, 30, hour = 9)),
                item(at(2026, 8, 29)),
            ),
            ZoomLevel.LARGE,
            zone,
        )

        val heads = headers(rows)
        assertEquals(2, heads.size)
        assertEquals(DateBucket(2026, 8, 30), heads[0].bucket)
        assertEquals(2, heads[0].count)
        assertEquals(DateBucket(2026, 8, 29), heads[1].bucket)
        assertEquals(1, heads[1].count)
    }

    @Test
    fun `zooming out to months merges the days inside them`() {
        val rows = buildTimeline(
            listOf(
                item(at(2026, 8, 30)),
                item(at(2026, 8, 2)),
                item(at(2026, 7, 31)),
            ),
            ZoomLevel.SMALL,
            zone,
        )

        val heads = headers(rows)
        assertEquals(2, heads.size)
        assertEquals(DateBucket(2026, 8, 0), heads[0].bucket)
        assertEquals(2, heads[0].count)
        assertEquals(DateBucket(2026, 7, 0), heads[1].bucket)
    }

    @Test
    fun `zooming out to years leaves one heading per year`() {
        val rows = buildTimeline(
            listOf(
                item(at(2026, 8, 30)),
                item(at(2026, 1, 2)),
                item(at(2025, 12, 31)),
            ),
            ZoomLevel.TINY,
            zone,
        )

        val heads = headers(rows)
        assertEquals(2, heads.size)
        assertEquals(DateBucket(2026, 0, 0), heads[0].bucket)
        assertEquals(2, heads[0].count)
        assertEquals(DateBucket(2025, 0, 0), heads[1].bucket)
    }

    @Test
    fun `a section fills whole rows and leaves the remainder short`() {
        val rows = buildTimeline(
            List(9) { item(at(2026, 8, 30)) },
            ZoomLevel.LARGE,
            zone,
        )

        val photos = photoRows(rows)
        assertEquals(3, photos.size)
        assertEquals(4, photos[0].items.size)
        assertEquals(4, photos[1].items.size)
        assertEquals(1, photos[2].items.size)
    }

    @Test
    fun `a new day starts a new row instead of filling the previous one`() {
        val rows = buildTimeline(
            listOf(
                item(at(2026, 8, 30)),
                item(at(2026, 8, 29)),
            ),
            ZoomLevel.LARGE,
            zone,
        )

        val photos = photoRows(rows)
        assertEquals(2, photos.size)
        assertEquals(1, photos[0].items.size)
        assertEquals(1, photos[1].items.size)
    }

    @Test
    fun `row keys are unique so the list can track them across zoom changes`() {
        val rows = buildTimeline(
            List(30) { item(at(2026, 8, 30 - it % 3)) },
            ZoomLevel.MEDIUM,
            zone,
        )

        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `a photo can be found again after the rows are re-cut`() {
        val items = List(20) { item(at(2026, 8, 30)) }
        val wide = buildTimeline(items, ZoomLevel.TINY, zone)
        val narrow = buildTimeline(items, ZoomLevel.HUGE, zone)
        val target = items[13].id

        assertEquals(1, wide.rowOf(target))
        assertEquals(7, narrow.rowOf(target))
    }

    @Test
    fun `looking for a photo that is gone reports no row`() {
        val rows = buildTimeline(listOf(item(at(2026, 8, 30))), ZoomLevel.LARGE, zone)
        assertEquals(-1, rows.rowOf(9999))
    }

    @Test
    fun `the anchor skips headings and lands on a photo`() {
        val rows = buildTimeline(List(4) { item(at(2026, 8, 30)) }, ZoomLevel.HUGE, zone)

        // Row 0 is the heading, so anchoring on it must resolve to the first photo.
        assertEquals(rows.filterIsInstance<TimelineRow.Photos>().first().items.first().id, rows.firstItemFrom(0)?.id)
        assertNull(buildTimeline(emptyList(), ZoomLevel.HUGE, zone).firstItemFrom(0))
    }

    @Test
    fun `zoom steps stop at the ends instead of wrapping`() {
        assertEquals(ZoomLevel.HUGE, ZoomLevel.HUGE.zoomIn())
        assertEquals(ZoomLevel.TINY, ZoomLevel.TINY.zoomOut())
        assertEquals(ZoomLevel.MEDIUM, ZoomLevel.LARGE.zoomOut())
        assertEquals(ZoomLevel.HUGE, ZoomLevel.LARGE.zoomIn())
    }

    @Test
    fun `midnight shots land on the day the clock showed, not UTC`() {
        // 00:30 Moscow on the 30th is still the 29th in UTC. Grouping by UTC
        // would quietly move every late-evening photo to the next day.
        val justAfterMidnight = at(2026, 8, 30, hour = 0)
        val rows = buildTimeline(listOf(item(justAfterMidnight)), ZoomLevel.LARGE, zone)

        assertEquals(DateBucket(2026, 8, 30), headers(rows).single().bucket)
    }

    @Test
    fun `the heading governing a row is the nearest one above it`() {
        val rows = buildTimeline(
            listOf(
                item(at(2026, 8, 30)),
                item(at(2026, 8, 30)),
                item(at(2026, 8, 29)),
            ),
            ZoomLevel.HUGE,
            zone,
        )

        // rows: [header 30th, photos, header 29th, photos]
        assertEquals(DateBucket(2026, 8, 30), rows.headerAt(0)?.bucket)
        assertEquals(DateBucket(2026, 8, 30), rows.headerAt(1)?.bucket)
        assertEquals(DateBucket(2026, 8, 29), rows.headerAt(2)?.bucket)
        assertEquals(DateBucket(2026, 8, 29), rows.headerAt(3)?.bucket)
    }

    @Test
    fun `asking past the end of the list still answers with the last heading`() {
        val rows = buildTimeline(listOf(item(at(2026, 8, 30))), ZoomLevel.HUGE, zone)
        assertEquals(DateBucket(2026, 8, 30), rows.headerAt(999)?.bucket)
    }

    @Test
    fun `an empty timeline has no heading anywhere`() {
        assertNull(emptyList<TimelineRow>().headerAt(0))
    }

    @Test
    fun `a pinch that barely moves changes nothing`() {
        assertEquals(0, zoomSteps(1f))
        assertEquals(0, zoomSteps(1.1f))
        assertEquals(0, zoomSteps(0.92f))
    }

    @Test
    fun `stretching apart asks for bigger tiles and squeezing for smaller`() {
        assertEquals(1, zoomSteps(1.4f))
        assertEquals(-1, zoomSteps(1f / 1.4f))
    }

    @Test
    fun `one gesture never moves more than one level, however wide it is`() {
        assertEquals(1, zoomSteps(12f))
        assertEquals(-1, zoomSteps(0.02f))
    }

    @Test
    fun `nonsense scales are ignored rather than crashing the grid`() {
        assertEquals(0, zoomSteps(0f))
        assertEquals(0, zoomSteps(-3f))
        assertEquals(0, zoomSteps(Float.NaN))
        assertEquals(0, zoomSteps(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `stepping walks the levels one at a time from wherever it is`() {
        // The bug this guards: stepping used to be computed from a level captured
        // when the grid was first drawn, so every pinch landed on the same two.
        assertEquals(ZoomLevel.MEDIUM, ZoomLevel.LARGE.stepBy(-1))
        assertEquals(ZoomLevel.SMALL, ZoomLevel.MEDIUM.stepBy(-1))
        assertEquals(ZoomLevel.TINY, ZoomLevel.SMALL.stepBy(-1))
        assertEquals(ZoomLevel.SMALL, ZoomLevel.TINY.stepBy(1))
        assertEquals(ZoomLevel.HUGE, ZoomLevel.LARGE.stepBy(1))
    }

    @Test
    fun `stepping stops at the ends instead of falling off them`() {
        assertEquals(ZoomLevel.TINY, ZoomLevel.TINY.stepBy(-1))
        assertEquals(ZoomLevel.HUGE, ZoomLevel.HUGE.stepBy(1))
        assertEquals(ZoomLevel.HUGE, ZoomLevel.TINY.stepBy(99))
        assertEquals(ZoomLevel.TINY, ZoomLevel.HUGE.stepBy(-99))
    }

    @Test
    fun `every level is reachable by stepping out from the default`() {
        var level = ZoomLevel.Default
        val seen = mutableListOf(level)
        repeat(ZoomLevel.entries.size) {
            level = level.stepBy(-1)
            if (seen.last() != level) seen += level
        }
        assertEquals(listOf(ZoomLevel.LARGE, ZoomLevel.MEDIUM, ZoomLevel.SMALL, ZoomLevel.TINY), seen)
        assertEquals(25, level.columns)
    }

    @Test
    fun `a heading knows every photo underneath it`() {
        val rows = buildTimeline(
            listOf(
                item(at(2026, 8, 30)),
                item(at(2026, 8, 30)),
                item(at(2026, 8, 30)),
                item(at(2026, 8, 29)),
            ),
            ZoomLevel.HUGE,
            zone,
        )

        // Two of the three land on one row and the third on the next, so this
        // has to walk rows rather than read a single one.
        assertEquals(3, rows.sectionItems(0).size)
    }

    @Test
    fun `a heading stops at the next heading rather than running to the end`() {
        val rows = buildTimeline(
            listOf(item(at(2026, 8, 30)), item(at(2026, 8, 29))),
            ZoomLevel.HUGE,
            zone,
        )

        val secondHeader = rows.indexOfFirst { it is TimelineRow.Header && it != rows.first() }
        assertEquals(1, rows.sectionItems(0).size)
        assertEquals(1, rows.sectionItems(secondHeader).size)
    }

    @Test
    fun `asking a photo row what it covers answers nothing`() {
        val rows = buildTimeline(listOf(item(at(2026, 8, 30))), ZoomLevel.HUGE, zone)
        assertTrue(rows.sectionItems(1).isEmpty())
        assertTrue(rows.sectionItems(99).isEmpty())
    }
}
