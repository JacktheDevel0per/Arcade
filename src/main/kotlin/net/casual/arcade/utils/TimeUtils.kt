package net.casual.arcade.utils

import net.casual.arcade.scheduler.MinecraftTimeDuration
import net.casual.arcade.scheduler.MinecraftTimeUnit
import net.casual.arcade.utils.TimeUtils.Minutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object TimeUtils {
    val Int.Ticks
        get() = MinecraftTimeUnit.Ticks.duration(this)

    val Int.RedstoneTicks
        get() = MinecraftTimeUnit.RedstoneTicks.duration(this)

    val Int.MinecraftDays
        get() = MinecraftTimeUnit.MinecraftDays.duration(this)

    val Int.Seconds
        get() = MinecraftTimeUnit.Seconds.duration(this)

    val Int.Minutes
        get() = MinecraftTimeUnit.Minutes.duration(this)

    val Int.Hours
        get() = MinecraftTimeUnit.Hours.duration(this)

    val Int.Days
        get() = MinecraftTimeUnit.Days.duration(this)

    fun toEpoch(time: LocalTime, zone: ZoneId): Long {
        return time.toEpochSecond(LocalDate.now(zone), zone.rules.getOffset(Instant.now()))
    }

    fun formatHHMMSS(duration: MinecraftTimeDuration): String {
        val seconds = duration.toSeconds().toInt()
        val hours = seconds / 3600
        return "%02d:".format(hours) + this.formatMMSS(duration)
    }

    fun formatMMSS(duration: MinecraftTimeDuration): String {
        val seconds = duration.toSeconds().toInt()
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}