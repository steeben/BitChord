package com.music.bitchord.ui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.AccentRed
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Stats page, opened from the account menu.
 *
 * The Replay headline cards used to sit in a row across the top of Library.
 * Here they stand one above the other at full width, each still opening Replay
 * at its own chart, and under them are the figures the cards have no room for:
 * how many days had music in them, the longest run of them, the song played
 * the most times, and the shape of an average day.
 *
 * Everything comes from the same [ReplayState] the Replay page reads, so the
 * two cannot disagree, and the period chips here move both.
 */
@Composable
fun StatsScreen(
    state: ReplayState,
    /** The signed-in account name embossed on the cards. */
    holder: String,
    onPeriodChange: (ReplayPeriod) -> Unit,
    onOpenReplay: (ReplayStoryPage) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val summary = state.summary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (dark) Color.White else Color.Black

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item("period") {
            Column(Modifier.padding(horizontal = PAGE_GUTTER + 4.dp)) {
                Spacer(Modifier.height(8.dp))
                PeriodPicker(state.period, onPeriodChange, dark)
                Spacer(Modifier.height(16.dp))
            }
        }
        when {
            state.loading && summary == null -> item("loading") {
                Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) {
                    CircularProgressIndicator(color = textColor.copy(alpha = 0.6f))
                }
            }
            summary == null || summary.isEmpty -> item("empty") { EmptyReplay(state.period, dark) }
            else -> {
                items(summary.cards(context), key = { "card-${it.label}" }) { card ->
                    ReplayCreditCard(
                        label = card.label,
                        value = card.value,
                        detail = card.detail,
                        artworkUrl = card.artworkUrl,
                        holder = holder,
                        memberSince = state.memberSince,
                        onClick = { onOpenReplay(card.page) },
                        modifier = Modifier
                            .padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                    )
                }
                item("more") { MoreStats(summary, dark) }
                item("replay") {
                    StatsActionRow(stringResource(R.string.stats_open_replay), dark) {
                        onOpenReplay(ReplayStoryPage.INTRO)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreStats(summary: ReplaySummary, dark: Boolean) {
    val context = LocalContext.current
    val textColor = if (dark) Color.White else Color.Black
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 4.dp)) {
        Text(
            text = stringResource(R.string.stats_more),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = textColor,
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.songs), grouped(summary.distinctSongs.toLong()), dark, Modifier.weight(1f))
            StatTile(stringResource(R.string.artists), grouped(summary.distinctArtists.toLong()), dark, Modifier.weight(1f))
            StatTile(stringResource(R.string.albums), grouped(summary.distinctAlbums.toLong()), dark, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.stats_plays), grouped(summary.totalPlays.toLong()), dark, Modifier.weight(1f))
            StatTile(
                stringResource(R.string.stats_days_listened),
                grouped(summary.activeDays.toLong()),
                dark,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(textColor.copy(alpha = 0.09f)),
        ) {
            if (summary.activeDays > 0) {
                FactRow(
                    label = stringResource(R.string.stats_daily_average),
                    value = formatListening(context, summary.averageActiveDayMs),
                    detail = stringResource(R.string.stats_daily_average_detail),
                    dark = dark,
                )
            }
            if (summary.longestStreak > 0) {
                FactRow(
                    label = stringResource(R.string.stats_longest_streak),
                    value = context.replayCount(summary.longestStreak, R.plurals.stats_day_count),
                    detail = stringResource(R.string.stats_streak_detail),
                    dark = dark,
                )
            }
            summary.mostReplayed?.let {
                FactRow(
                    label = stringResource(R.string.stats_on_repeat),
                    value = it.song.title,
                    detail = "${it.song.artist} · ${context.replayCount(it.plays, R.plurals.replay_play_count)}",
                    dark = dark,
                )
            }
            summary.genres.firstOrNull()?.let {
                FactRow(
                    label = stringResource(R.string.stats_top_genre),
                    value = it.title,
                    detail = formatListening(context, it.ms),
                    dark = dark,
                )
            }
            summary.peakHour?.let {
                FactRow(
                    label = stringResource(R.string.stats_peak_hour),
                    value = formatHour(context, it),
                    detail = null,
                    dark = dark,
                )
            }
            summary.busiestDay?.let {
                FactRow(
                    label = stringResource(R.string.stats_biggest_day),
                    value = formatDay(context, it),
                    detail = formatListening(context, summary.busiestDayMs),
                    dark = dark,
                )
            }
            // All time only: on a shorter period the first month is just the
            // start of that period, which says nothing.
            firstMonth(summary.since)?.takeIf { summary.period == ReplayPeriod.ALL_TIME }?.let {
                FactRow(
                    label = stringResource(R.string.stats_listening_since),
                    value = it,
                    detail = null,
                    dark = dark,
                )
            }
        }

        if (summary.hourOfDay.any { it > 0 }) {
            Text(
                text = stringResource(R.string.stats_by_hour),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W700,
                color = textColor,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
            )
            HourChart(summary.hourOfDay, summary.peakHour, dark)
        }
    }
}

/** One fact: a small label, the figure, and a line saying what it is of. */
@Composable
private fun FactRow(label: String, value: String, detail: String?, dark: Boolean) {
    val textColor = if (dark) Color.White else Color.Black
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W700,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Listening by hour of the day, midnight to midnight, with the peak hour in the
 * accent and the rest in ink. Labelled at the four quarters only: twenty-four
 * labels under twenty-four thin bars is a row of noise.
 */
@Composable
private fun HourChart(hours: List<Long>, peak: Int?, dark: Boolean) {
    val context = LocalContext.current
    val textColor = if (dark) Color.White else Color.Black
    val max = hours.maxOrNull()?.takeIf { it > 0 } ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(textColor.copy(alpha = 0.09f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEachIndexed { hour, ms ->
                val fraction = (ms.toFloat() / max).coerceIn(0.03f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (hour == peak) AccentRed else textColor.copy(alpha = 0.35f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    text = formatHour(context, hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatsActionRow(label: String, dark: Boolean, onClick: () -> Unit) {
    val textColor = if (dark) Color.White else Color.Black
    Row(
        Modifier
            .padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(textColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Play,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** `2025-03` as "March 2025". */
private fun firstMonth(since: String?): String? = since?.let {
    runCatching {
        YearMonth.parse(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            .replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
    }.getOrNull()
}
