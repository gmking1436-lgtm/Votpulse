package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.ui.touch.InstantHapticType
import com.example.ui.touch.instantTap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VoltElectricCyan
import com.example.ui.theme.VoltGreen
import com.example.ui.theme.VoltPulseTheme
import com.example.ui.theme.VoltRed

/**
 * Material Design 3 Sound Alert Settings Section.
 *
 * Controls event triggers for:
 * 1. Charger Connected Sound
 * 2. Charger Disconnected Sound
 * 3. 100% Full Charge Alarm
 *
 * Integrates SAF file picking (OpenDocument audio mime-type) with instant playback previews
 * through a unified, singleton [PreviewTarget] audio engine to prevent overlapping audio.
 */
@Composable
fun SoundSettingsSection(
    isPluggedEnabled: Boolean,
    onPluggedEnabledChange: (Boolean) -> Unit,
    pluggedUri: String?,
    onPluggedUriSelected: (Uri?) -> Unit,
    isUnpluggedEnabled: Boolean,
    onUnpluggedEnabledChange: (Boolean) -> Unit,
    unpluggedUri: String?,
    onUnpluggedUriSelected: (Uri?) -> Unit,
    isFullChargeEnabled: Boolean,
    onFullChargeEnabledChange: (Boolean) -> Unit,
    fullChargeUri: String?,
    onFullChargeUriSelected: (Uri?) -> Unit,
    isVoiceAnnouncementEnabled: Boolean = false,
    onVoiceAnnouncementEnabledChange: (Boolean) -> Unit = {},
    onTestVoiceAnnouncement: () -> Unit = {},
    currentPlayingPreview: PreviewTarget,
    onTogglePreview: (PreviewTarget, String?) -> Unit,
    onStopPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Release active preview audio when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            onStopPreview()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AUDIO ALERTS & SOUNDS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // 1. Charger Connected Sound
        SoundAlertCard(
            title = "Charger Connected Alert",
            description = "Play short sound when power is connected",
            icon = Icons.Default.Power,
            accentColor = VoltElectricCyan,
            enabled = isPluggedEnabled,
            onEnabledChange = { enabled ->
                if (!enabled && currentPlayingPreview == PreviewTarget.CONNECTED) {
                    onStopPreview()
                }
                onPluggedEnabledChange(enabled)
            },
            uriString = pluggedUri,
            onUriSelected = onPluggedUriSelected,
            isPlaying = currentPlayingPreview == PreviewTarget.CONNECTED,
            onPreviewToggle = {
                onTogglePreview(PreviewTarget.CONNECTED, pluggedUri)
            },
            testTagPrefix = "plugged_sound"
        )

        // 2. Charger Disconnected Sound
        SoundAlertCard(
            title = "Charger Disconnected Alert",
            description = "Play short sound when power is removed",
            icon = Icons.Default.PowerOff,
            accentColor = VoltRed,
            enabled = isUnpluggedEnabled,
            onEnabledChange = { enabled ->
                if (!enabled && currentPlayingPreview == PreviewTarget.DISCONNECTED) {
                    onStopPreview()
                }
                onUnpluggedEnabledChange(enabled)
            },
            uriString = unpluggedUri,
            onUriSelected = onUnpluggedUriSelected,
            isPlaying = currentPlayingPreview == PreviewTarget.DISCONNECTED,
            onPreviewToggle = {
                onTogglePreview(PreviewTarget.DISCONNECTED, unpluggedUri)
            },
            testTagPrefix = "unplugged_sound"
        )

        // 3. 100% Full Charge Alarm
        SoundAlertCard(
            title = "100% Full Charge Alarm",
            description = "Continuous alarm when battery reaches 100%",
            icon = Icons.Default.NotificationsActive,
            accentColor = VoltGreen,
            enabled = isFullChargeEnabled,
            onEnabledChange = { enabled ->
                if (!enabled && currentPlayingPreview == PreviewTarget.FULL_CHARGE) {
                    onStopPreview()
                }
                onFullChargeEnabledChange(enabled)
            },
            uriString = fullChargeUri,
            onUriSelected = onFullChargeUriSelected,
            isPlaying = currentPlayingPreview == PreviewTarget.FULL_CHARGE,
            onPreviewToggle = {
                onTogglePreview(PreviewTarget.FULL_CHARGE, fullChargeUri)
            },
            testTagPrefix = "full_charge_sound"
        )

        // 4. Dynamic Voice Announcements (Text-to-Speech)
        VoiceAnnouncementCard(
            enabled = isVoiceAnnouncementEnabled,
            onEnabledChange = onVoiceAnnouncementEnabledChange,
            onTestSpeech = onTestVoiceAnnouncement
        )
    }
}

@Composable
private fun VoiceAnnouncementCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestSpeech: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_announcement_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (enabled) Color(0xFF00E5FF).copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = if (enabled) 0.15f else 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Voice Announcements",
                        tint = if (enabled) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice Announcements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Speaks battery percentage & live wattage aloud on connect, disconnect, and full charge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("voice_announcement_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = Color(0xFF00E5FF)
                    )
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onTestSpeech,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .testTag("test_voice_announcement_button")
                            .instantTap(InstantHapticType.CLICK) { onTestSpeech() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Test Speech",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test Voice Announcement",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundAlertCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    uriString: String?,
    onUriSelected: (Uri?) -> Unit,
    isPlaying: Boolean,
    onPreviewToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "sound_card"
) {
    val context = LocalContext.current
    val fileName = remember(uriString) {
        getDisplayNameFromUri(context, uriString)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onUriSelected(uri)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (enabled) accentColor.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = if (enabled) 0.15f else 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("${testTagPrefix}_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (uriString != null) {
                            IconButton(
                                onClick = { onUriSelected(null) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("${testTagPrefix}_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Reset to system default",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launcher.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("${testTagPrefix}_pick_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Choose Tone",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        FilledTonalButton(
                            onClick = onPreviewToggle,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("${testTagPrefix}_preview_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isPlaying) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = accentColor.copy(alpha = 0.25f),
                                    contentColor = accentColor
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Stop" else "Play",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) "Stop" else "Test Sound",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getDisplayNameFromUri(context: Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) return "System Default Tone"
    return try {
        val uri = Uri.parse(uriString)
        var displayName: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        displayName = cursor.getString(index)
                    }
                }
            }
        }

        displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Custom Audio File"
    } catch (e: Exception) {
        "Custom Audio File"
    }
}

@Preview(showBackground = true)
@Composable
fun SoundSettingsSectionPreview() {
    VoltPulseTheme {
        SoundSettingsSection(
            isPluggedEnabled = true,
            onPluggedEnabledChange = {},
            pluggedUri = null,
            onPluggedUriSelected = {},
            isUnpluggedEnabled = true,
            onUnpluggedEnabledChange = {},
            unpluggedUri = null,
            onUnpluggedUriSelected = {},
            isFullChargeEnabled = true,
            onFullChargeEnabledChange = {},
            fullChargeUri = null,
            onFullChargeUriSelected = {},
            currentPlayingPreview = PreviewTarget.NONE,
            onTogglePreview = { _, _ -> },
            onStopPreview = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
