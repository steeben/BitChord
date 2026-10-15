package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.AlbumArtChoices
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt

/**
 * Picks which of the covers an album's own files carry stands for the album —
 * see [AlbumArtChoices]. Tapping a cover keeps it and closes; the row under
 * the grid goes back to the one Android chose.
 */
@Composable
fun AlbumArtPickerSheet(
    target: BrowseTarget,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val choices by AlbumArtChoices.choices.collectAsStateWithLifecycle()
    val current = AlbumArtChoices.chosen(target.browseId, target.title, choices)
    val candidates by produceState<List<String>?>(null, target) {
        value = AlbumArtChoices.candidates(context, target.songs)
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.choose_album_art),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = target.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
        val found = candidates
        when {
            found == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            found.isEmpty() -> Text(
                text = stringResource(R.string.no_album_art_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(found, key = { it }) { url ->
                    val picked = url == current
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (picked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
                            )
                            .clickable {
                                AlbumArtChoices.set(target.browseId, target.title, url)
                                onDone()
                            },
                    ) {
                        AsyncImage(
                            model = url.artworkAt(CARD_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        if (picked) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(3.dp),
                            )
                        }
                    }
                }
            }
        }
        }
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            ActionRow(Icons.Rounded.Restore, stringResource(R.string.use_original_album_art)) {
                AlbumArtChoices.clear(target.browseId, target.title)
                onDone()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
