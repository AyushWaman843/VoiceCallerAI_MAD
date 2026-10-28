package com.ayush.madv2.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CallPresentationTest {
    @Test
    fun utcTimeDisplaysOnCorrectIstDay() {
        assertEquals("Thu, 1 Oct at 1:30 AM IST", formatCallTime("2026-09-30T20:00:00Z"))
    }

    @Test
    fun malformedLegacyTimesRemainVisible() {
        assertEquals("legacy time", formatCallTime("legacy time"))
        assertEquals("Time not set", formatCallTime(null))
    }
}
