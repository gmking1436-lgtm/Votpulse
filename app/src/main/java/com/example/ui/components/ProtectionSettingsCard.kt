package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ChargingPowerTelemetry
import com.example.service.ChargingSpeedCategory
import com.example.ui.touch.InstantHapticType
import com.example.ui.touch.instantTap
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Advanced Hardware Protection & Battery Longevity Control Card.
 *
 * Implements:
 * 1. Custom Alarm Target Slider (80% - 100%) with snap pill selectors.
 * 2. High Temperature / Overheat Alarm toggle with selectable thresholds (40°C, 42°C, 45°C).
 * 3. Live charging telemetry chips showing real-time Watts, Voltage, and Battery Health.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtectionSettingsCard(
    targetPercentage: Int,
    onTargetPercentageChange: (Int) -> Unit,
    isOverheatAlertEnabled: Boolean,
    onOverheatAlertEnabledChange: (Boolean) -> Unit,
    overheatThresholdCelsius: Float,
    onOverheatThresholdChange: (Float) -> Unit,
    currentTemperatureCelsius: Float,
    telemetry: ChargingPowerTelemetry,
    batteryHealth: String,
    modifier: Modifier = Modifier
) {
    val snapMarks = listOf(80, 85, 90, 95, 100)
    val temperatureThresholds = listOf(40.0f, 42.0f, 45.0f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("protection_settings_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hardware Protection",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Longevity guard & live charging diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ====================================================================
            // SECTION 1: Live Charging Telemetry Stats Chips
            // ====================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "LIVE CHARGING TELEMETRY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Wattage & Speed Chip
                    val speedColor = when (telemetry.speedCategory) {
                        ChargingSpeedCategory.ULTRA_FAST -> Color(0xFF00E5FF)
                        ChargingSpeedCategory.FAST -> Color(0xFF00E676)
                        ChargingSpeedCategory.SLOW -> Color(0xFFFFB300)
                        ChargingSpeedCategory.DISCHARGING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    TelemetryChip(
                        icon = Icons.Default.Bolt,
                        label = if (telemetry.watts > 0) String.format(Locale.US, "%.1f W", telemetry.watts) else "0.0 W",
                        sublabel = telemetry.speedCategory.label,
                        accentColor = speedColor,
                        testTag = "telemetry_chip_watts"
                    )

                    // Voltage Chip
                    val voltageVolts = telemetry.voltageMilliVolts / 1000f
                    TelemetryChip(
                        icon = Icons.Default.Speed,
                        label = String.format(Locale.US, "%.2f V", voltageVolts),
                        sublabel = "${telemetry.voltageMilliVolts} mV",
                        accentColor = MaterialTheme.colorScheme.primary,
                        testTag = "telemetry_chip_voltage"
                    )

                    // Battery Health Chip
                    val healthColor = if (batteryHealth.equals("Good", ignoreCase = true)) {
                        Color(0xFF00E676)
                    } else {
                        Color(0xFFFF7043)
                    }
                    TelemetryChip(
                        icon = Icons.Default.Favorite,
                        label = batteryHealth,
                        sublabel = "Cell Health",
                        accentColor = healthColor,
                        testTag = "telemetry_chip_health"
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            // ====================================================================
            // SECTION 2: Custom Alarm Target Slider (80% - 100%)
            // ====================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Longevity Charge Target",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Triggers alarm when reaching target percentage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Target pill badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.testTag("target_percentage_badge")
                    ) {
                        Text(
                            text = "$targetPercentage%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Snap Marks Pill Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    snapMarks.forEach { mark ->
                        val isSelected = targetPercentage == mark
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(200),
                            label = "markBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                            label = "markText"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .instantTap(hapticType = InstantHapticType.TICK) {
                                    onTargetPercentageChange(mark)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$mark%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                    }
                }

                // High-Touch Sampling Rate Zero-Jank Snap Slider
                LowLatencySnapSlider(
                    value = targetPercentage,
                    onValueChange = onTargetPercentageChange,
                    minValue = 80,
                    maxValue = 100,
                    stepInterval = 5,
                    testTag = "target_percentage_slider",
                    modifier = Modifier.fillMaxWidth()
                )

                // Battery longevity tip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (targetPercentage <= 85) {
                            "Setting target to $targetPercentage% preserves cathode integrity, extending cycle lifespan up to 2.5×."
                        } else {
                            "Higher charging targets prioritize maximum single-charge runtime over long-term battery cell health."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            // ====================================================================
            // SECTION 3: High Temperature / Overheat Alarm
            // ====================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isOverheatAlertEnabled) Color(0xFFFF5252).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (isOverheatAlertEnabled) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Overheat Protection Alert",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Continuous heads-up audio alarm if thermals spike",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LowLatencySwitch(
                        checked = isOverheatAlertEnabled,
                        onCheckedChange = onOverheatAlertEnabledChange,
                        modifier = Modifier.testTag("overheat_alert_switch"),
                        checkedThumbColor = Color(0xFFFF5252),
                        checkedTrackColor = Color(0xFFFF5252).copy(alpha = 0.35f)
                    )
                }

                // Expandable threshold selector when enabled
                AnimatedVisibility(
                    visible = isOverheatAlertEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trigger Temperature Threshold",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Current thermals readout pill
                            val isCritical = currentTemperatureCelsius >= overheatThresholdCelsius
                            val tempColor = if (isCritical) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(tempColor)
                                )
                                Text(
                                    text = String.format(Locale.US, "Current: %.1f°C", currentTemperatureCelsius),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = tempColor
                                )
                            }
                        }

                        // Selectable threshold chips (40°C, 42°C, 45°C)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            temperatureThresholds.forEach { threshold ->
                                val isSelected = overheatThresholdCelsius == threshold
                                val label = when (threshold) {
                                    40.0f -> "40°C (Sensitive)"
                                    42.0f -> "42°C (Default)"
                                    else -> "45°C (Relaxed)"
                                }

                                val chipBg by animateColorAsState(
                                    targetValue = if (isSelected) Color(0xFFFF5252).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                    animationSpec = tween(200),
                                    label = "tempChipBg"
                                )
                                val chipBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(0xFFFF5252) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    animationSpec = tween(200),
                                    label = "tempChipBorder"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(chipBg)
                                        .border(1.dp, chipBorderColor, RoundedCornerShape(10.dp))
                                        .instantTap(hapticType = InstantHapticType.TICK) {
                                            onOverheatThresholdChange(threshold)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact high-contrast chip for live hardware charging telemetry.
 */
@Composable
private fun TelemetryChip(
    icon: ImageVector,
    label: String,
    sublabel: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                accentColor.copy(alpha = 0.25f)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
