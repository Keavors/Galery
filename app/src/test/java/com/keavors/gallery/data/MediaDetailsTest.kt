package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureTest {

    @Test
    fun `fast shutter speeds read as a fraction, the way a camera shows them`() {
        // "0.004 s" is the same number and useless to a person.
        assertEquals("1/250", formatExposure(1.0 / 250))
        assertEquals("1/60", formatExposure(1.0 / 60))
        assertEquals("1/8000", formatExposure(1.0 / 8000))
    }

    @Test
    fun `long exposures read as seconds`() {
        assertEquals("1", formatExposure(1.0))
        assertEquals("2.5", formatExposure(2.5))
        assertEquals("30", formatExposure(30.0))
    }

    @Test
    fun `a missing shutter speed produces nothing rather than a zero`() {
        assertEquals("", formatExposure(0.0))
        assertEquals("", formatExposure(-1.0))
    }
}

class ApertureAndFocalTest {

    @Test
    fun `aperture keeps the f and drops a pointless decimal`() {
        assertEquals("f/1.8", formatAperture(1.8))
        assertEquals("f/2", formatAperture(2.0))
        assertEquals("f/11", formatAperture(11.0))
    }

    @Test
    fun `focal length drops a pointless decimal too`() {
        assertEquals("24", formatFocalLength(24.0))
        assertEquals("6.7", formatFocalLength(6.7))
    }
}

class ResolutionTest {

    @Test
    fun `resolution uses the multiplication sign, not the letter`() {
        assertEquals("4032 × 3024", formatResolution(4032, 3024))
    }

    @Test
    fun `megapixels round to one decimal`() {
        assertEquals("12.2", formatMegapixels(4032, 3024))
        assertEquals("108.0", formatMegapixels(12000, 9000))
    }

    @Test
    fun `an unknown size shows nothing instead of zeroes`() {
        assertEquals("", formatResolution(0, 0))
        assertEquals("", formatMegapixels(0, 3024))
    }
}
