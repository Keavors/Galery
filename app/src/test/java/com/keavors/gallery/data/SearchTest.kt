package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * What a search finds.
 *
 * Dates are the part worth guarding: a photograph is found by a year, by the
 * name of a month in either language, or by a whole date written the way people
 * write one — and none of that may be confused with a file called "2019.jpg".
 */
class SearchTest {

    private val zone = ZoneId.of("UTC")
    private val ru = Locale.forLanguageTag("ru")

    private fun at(date: String, id: Long = 1, name: String = "IMG_$id.jpg", bucket: Long = 1) =
        testItem(id = id, name = name, bucket = bucket).copy(
            takenAt = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

    private fun List<MediaItem>.find(query: String) =
        matching(parseSearch(query, ru), zone).map { it.id }

    private val library = listOf(
        at("2026-08-14", id = 1, name = "IMG_0001.jpg", bucket = 1).copy(bucketName = "Camera"),
        at("2019-01-02", id = 2, name = "море.jpg", bucket = 2).copy(bucketName = "Отпуск"),
        at("2019-08-03", id = 3, name = "clip.mp4", bucket = 2).copy(
            bucketName = "Отпуск",
            isVideo = true,
        ),
    )

    @Test
    fun `an empty search is not a search`() {
        assertEquals(listOf(1L, 2L, 3L), library.find("   "))
    }

    @Test
    fun `by name, whatever the case`() {
        assertEquals(listOf(2L), library.find("МОРЕ"))
        assertEquals(listOf(1L), library.find("img_0001"))
    }

    @Test
    fun `by folder`() {
        assertEquals(listOf(2L, 3L), library.find("отпуск"))
    }

    @Test
    fun `by kind`() {
        assertEquals(listOf(3L), library.find("видео"))
        assertEquals(listOf(1L, 2L), library.find("фото"))
    }

    @Test
    fun `by year`() {
        assertEquals(listOf(2L, 3L), library.find("2019"))
    }

    @Test
    fun `by the name of a month, in either language and either form`() {
        assertEquals(listOf(1L, 3L), library.find("август"))
        assertEquals(listOf(1L, 3L), library.find("августа"))
        assertEquals(listOf(1L, 3L), library.find("august"))
    }

    @Test
    fun `by a whole date, written either way round`() {
        assertEquals(listOf(1L), library.find("14.08.2026"))
        assertEquals(listOf(1L), library.find("2026-08-14"))
    }

    @Test
    fun `a whole date is not also its year`() {
        // 03.08.2019 must not drag in everything from 2019 or everything from
        // August: somebody who typed a day meant that day.
        assertEquals(listOf(3L), library.find("03.08.2019"))
    }

    @Test
    fun `two words narrow rather than widen`() {
        assertEquals(listOf(3L), library.find("отпуск август"))
        assertTrue(library.find("отпуск 2026").isEmpty())
    }

    @Test
    fun `a name that looks like a year is still a name`() {
        val named = listOf(at("2001-05-05", id = 9, name = "2019.jpg"))
        assertEquals(listOf(9L), named.find("2019"))
    }

    @Test
    fun `two letters are not a month`() {
        // "ма" would have to choose between май and март, so it stays a word and
        // matches nothing but names and folders.
        assertTrue(library.find("ма").isEmpty())
    }

    @Test
    fun `a word that is nothing at all finds nothing`() {
        assertTrue(library.find("qwerty").isEmpty())
        assertFalse(library.find("qwerty").contains(1L))
    }
}
