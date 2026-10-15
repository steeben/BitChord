package com.music.bitchord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.screens.SegmentedControl
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState

/**
 * The card that opens on holding the title beside the Home logo: one field to
 * type whatever the title should say, everything else about the dial (its
 * grid, the artists' dial, the die, the pin badges — applied as they are
 * touched), then Save, a way back to the default, Cancel.
 */
@Composable
fun HomeTitleAlert(
    hazeState: HazeState,
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_title_edit),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_title_edit_desc),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            PillTextField(
                value = text,
                onValueChange = { text = it.take(CustomizationSettings.HOME_TITLE_MAX) },
                placeholder = CustomizationSettings.DEFAULT_HOME_TITLE,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            )
            DialOptions(Modifier.padding(top = 16.dp))
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = { onSave(text) },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.home_title_reset),
            emphasised = false,
            onClick = { onSave("") },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
        )
    }
}

/** The dial's own settings, the same ones Settings › Layout has. */
@Composable
private fun DialOptions(modifier: Modifier = Modifier) {
    val rows by CustomizationSettings.speedDialRows.collectAsStateWithLifecycle()
    val artistDial by CustomizationSettings.artistDial.collectAsStateWithLifecycle()
    val feelingLucky by CustomizationSettings.feelingLucky.collectAsStateWithLifecycle()
    val pinIcon by CustomizationSettings.dialPinIcon.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.speed_dial_grid),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        SegmentedControl(
            options = listOf("3 × 3", "3 × 4"),
            selectedIndex = rows - 3,
            onSelect = { CustomizationSettings.setSpeedDialRows(it + 3) },
            modifier = Modifier.padding(bottom = 6.dp),
        )
        DialSwitch(stringResource(R.string.artist_dial), artistDial, CustomizationSettings::setArtistDial)
        DialSwitch(stringResource(R.string.feeling_lucky_setting), feelingLucky, CustomizationSettings::setFeelingLucky)
        DialSwitch(stringResource(R.string.dial_pin_icon), pinIcon, CustomizationSettings::setDialPinIcon)
    }
}

@Composable
private fun DialSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
