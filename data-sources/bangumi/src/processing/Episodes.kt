package me.him188.ani.datasources.bangumi.processing

import org.openapitools.client.models.Episode
import org.openapitools.client.models.EpisodeDetail
import org.openapitools.client.models.SlimSubject
import org.openapitools.client.models.Subject
import java.time.LocalDate
import java.time.ZoneOffset

fun EpisodeDetail.nameCNOrName() =
    nameCn.takeIf { it.isNotBlank() } ?: name

fun Episode.nameCNOrName() =
    nameCn.takeIf { it.isNotBlank() } ?: name


/**
 * 剧集在今天或者未来播出时返回 `true`. 解析失败时返回 `null`.
 */
fun Episode.isOnAir(): Boolean? {
    val airDate = parseAirDate(airdate) ?: return null
    return !LocalDate.now(ZoneOffset.ofHours(+8)).isAfter(airDate)
}

fun parseAirDate(date: String): LocalDate? {
    val split = date.split("-")
    if (split.size != 3) {
        return null
    }
    val (year, month, day) = split
    return LocalDate.of(year.toInt(), month.toInt(), day.toInt())
}

val Subject.airSeason get() = date?.let { categorizeAirDate(it) }
val SlimSubject.airSeason get() = date?.let { categorizeAirDate(it) }

fun categorizeAirDate(date: String): String {
    val split = date.split("-")
    if (split.size != 3) {
        return date
    }
    val month = when (split[1].toIntOrNull() ?: return date) {
        12,
        in 1..2 -> "1"

        in 3..5 -> "4"
        in 6..8 -> "7"
        in 9..11 -> "10"
        else -> null
    }
    return if (month == null) {
        "${split[0]} 年"
    } else {
        "${split[0]} 年 $month 月"
    }
}