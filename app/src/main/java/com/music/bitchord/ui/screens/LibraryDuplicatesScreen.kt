package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.AlbumArtChoices
import com.music.bitchord.data.DuplicateChoices
import com.music.bitchord.data.DuplicateGroup
import com.music.bitchord.data.hasYouTubeId
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.frostedSurfaceColor
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import java.util.Locale

/**
 * Settings → Hide detected duplicates → the sets themselves: every song the
 * All library holds more than one copy of, what each copy is, and for each
 * song whether it shows once, whose details it shows and which copy plays.
 *
 * A copy's row plays that copy, so telling the file from the stream is a tap
 * rather than a guess.
 */
@Composable
fun LibraryDuplicatesScreen(
    state: UiState<List<DuplicateGroup>>,
    contentPadding: PaddingValues,
    onPlayCopy: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide by AppSettings.hideCombinedDuplicates.collectAsStateWithLifecycle()
    val choices by AppSettings.duplicateChoices.collectAsStateWithLifecycle()
    val accent = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    val groups = (state as? UiState.Success)?.data.orEmpty()
    val sorted = remember(groups) {
        groups.sortedBy { (it.youTubeCopy ?: it.members.first()).title.lowercase(Locale.ROOT) }
    }
    val (once, apart) = remember(sorted, choices) { sorted.partition { it.key !in choices.keptApart } }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "hide") {
            SettingsGroup(
                topSpacing = 8.dp,
                footer = stringResource(
                    if (hide) R.string.library_duplicates_about else R.string.library_duplicates_off,
                ),
            ) {
                SettingsRow(
                    icon = Icons.Rounded.FilterAlt,
                    title = stringResource(R.string.hide_combined_duplicates),
                    trailing = {
                        Switch(
                            checked = hide,
                            onCheckedChange = AppSettings::setHideCombinedDuplicates,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setHideCombinedDuplicates(!hide) },
                )
            }
        }
        when {
            state is UiState.Loading -> item(key = "loading") {
                Message(stringResource(R.string.library_duplicates_loading))
            }
            groups.isEmpty() -> item(key = "none") {
                Message(stringResource(R.string.library_duplicates_none))
            }
            else -> {
                if (once.isNotEmpty()) {
                    item(key = "once") { Header(stringResource(R.string.library_duplicates_shown_once)) }
                    items(once, key = { "once:" + it.key }) { group ->
                        DuplicateCard(group, choices, apart = false, accent = accent, onPlayCopy = onPlayCopy)
                    }
                }
                if (apart.isNotEmpty()) {
                    item(key = "apart") { Header(stringResource(R.string.library_duplicates_shown_apart)) }
                    items(apart, key = { "apart:" + it.key }) { group ->
                        DuplicateCard(group, choices, apart = true, accent = accent, onPlayCopy = onPlayCopy)
                    }
                }
                if (choices != DuplicateChoices()) {
                    item(key = "reset") {
                        Text(
                            text = stringResource(R.string.duplicate_reset),
                            style = MaterialTheme.typography.bodyLarge,
                            color = accent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp)
                                .clickable { AppSettings.setDuplicateChoices(DuplicateChoices()) }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = GROUP_INSET + 4.dp, end = GROUP_INSET, top = 26.dp, bottom = 8.dp),
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
    )
}

/** One song: what it shows as, each copy of it, and the choices it offers. */
@Composable
private fun DuplicateCard(
    group: DuplicateGroup,
    choices: DuplicateChoices,
    /** Whether the listener has this set showing as separate songs. */
    apart: Boolean,
    accent: Color,
    onPlayCopy: (Song) -> Unit,
) {
    val shown = remember(group, choices) { group.shown(choices) { AlbumArtChoices.songArtFor(it.localUri) } }
    val playing = when {
        apart -> null
        group.playsStream(choices) -> group.streamCopy
        else -> group.deviceCopy ?: group.members.first()
    }
    fun update(change: DuplicateChoices.() -> DuplicateChoices) = AppSettings.setDuplicateChoices(choices.change())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET, vertical = 6.dp)
            .clip(GroupShape)
            .background(frostedSurfaceColor(MaterialTheme.colorScheme.surfaceVariant)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ROW_INSET, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = shown.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = shown.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shown.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        group.members.forEach { copy ->
            RowDivider()
            CopyRow(copy, plays = copy === playing, accent = accent, onPlay = { onPlayCopy(copy) })
        }
        if (!apart && group.canChoosePlayback) {
            RowDivider()
            ChoiceRow(
                label = stringResource(R.string.duplicate_plays),
                options = listOf(
                    stringResource(R.string.duplicate_source_device),
                    stringResource(R.string.duplicate_source_youtube),
                ),
                selected = if (group.playsStream(choices)) 1 else 0,
                accent = accent,
                onSelect = { index -> update { copy(playStream = playStream.toggled(group.key, index == 1)) } },
            )
        }
        if (!apart && group.canChooseDetails) {
            RowDivider()
            ChoiceRow(
                label = stringResource(R.string.duplicate_details),
                options = listOf(
                    stringResource(R.string.duplicate_source_youtube),
                    stringResource(R.string.duplicate_file_tags),
                ),
                selected = if (group.showsFileDetails(choices)) 1 else 0,
                accent = accent,
                onSelect = { index -> update { copy(fileDetails = fileDetails.toggled(group.key, index == 1)) } },
            )
        }
        RowDivider()
        Text(
            text = stringResource(if (apart) R.string.duplicate_show_once else R.string.duplicate_keep_apart),
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { update { copy(keptApart = keptApart.toggled(group.key, !apart)) } }
                .padding(horizontal = ROW_INSET, vertical = 14.dp),
        )
    }
}

private fun Set<String>.toggled(key: String, on: Boolean): Set<String> = if (on) this + key else this - key

/** A copy: where it is, what it says it is, and whether it is the one that plays. */
@Composable
private fun CopyRow(copy: Song, plays: Boolean, accent: Color, onPlay: () -> Unit) {
    val streams = copy.localUri == null
    val downloaded = !streams && copy.hasYouTubeId()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = MaterialTheme.colorScheme.onBackground
        val iconModifier = Modifier.size(ICON_SIZE)
        when {
            streams -> Icon(painterResource(R.drawable.ic_youtube_music), null, tint = tint, modifier = iconModifier)
            downloaded -> Icon(Icons.Rounded.DownloadDone, null, tint = tint, modifier = iconModifier)
            else -> Icon(Icons.Rounded.PhoneAndroid, null, tint = tint, modifier = iconModifier)
        }
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when {
                            streams -> R.string.duplicate_source_youtube
                            downloaded -> R.string.duplicate_source_downloaded
                            else -> R.string.duplicate_source_device
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                if (plays) {
                    Spacer(Modifier.width(8.dp))
                    Badge(stringResource(R.string.duplicate_plays))
                }
            }
            Text(
                text = listOfNotNull(copy.title, copy.artist, copy.durationText).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!streams && !downloaded) {
                copy.localPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { file ->
                    Text(
                        text = file,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.duplicate_play_copy),
            tint = accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** A two-way choice: its label, then the options as a pair of pills. */
@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(3.dp),
        ) {
            options.forEachIndexed { index, option ->
                val on = index == selected
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { if (!on) onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
