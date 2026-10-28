package com.ayush.madv2.ui

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatCallTime(value: String?): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(CallCalendarZone)
        .format(DateTimeFormatter.ofPattern("EEE, d MMM 'at' h:mm a 'IST'", Locale.ENGLISH))
}.getOrElse { value?.takeIf { it.isNotBlank() } ?: "Time not set" }
