package com.parlor.core.design

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** UI-thread formatters for display only. Stored dates, IDs and coordinates stay canonical. */
class DisplayFormats(private val locale: Locale) {
    private val integers = NumberFormat.getIntegerInstance(locale)
    private val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val digits = DecimalStyle.of(locale)
    private val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withDecimalStyle(digits)
    private val timestamps = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withDecimalStyle(digits)

    fun integer(value: Number): String = integers.format(value)
    fun decimal(value: Number): String = decimals.format(value)
    fun date(value: String): String = date(LocalDate.parse(value))
    fun date(value: LocalDate): String = dates.format(value)
    fun timestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timestamps.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    fun clock(minuteOfDay: Int, use24Hours: Boolean): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hours) "Hm" else "hm")
        return DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(digits)
            .format(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
    }
}

@Composable
fun rememberDisplayFormats(): DisplayFormats {
    // Use the same resources as stringResource, including inherited resources in dialogs.
    // LocalResources invalidates its readers when the Android configuration changes.
    val locale = LocalResources.current.configuration.locales[0]
    return remember(locale) { DisplayFormats(locale) }
}
