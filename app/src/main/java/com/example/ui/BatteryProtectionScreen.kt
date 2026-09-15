package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.VoltAmber
import com.example.ui.theme.VoltElectricCyan
import com.example.ui.theme.VoltGreen
import com.example.ui.theme.VoltRed
import kotlin.math.roundToInt

/**
 * Battery Protection Suite Screen.
 *
 * Provides granular, hardware-level longevity and thermal safeguards:
 * 1. Protection Status Card with real-time status badge and cell degradation rationale.
 * 2. High-precision discrete snap Slider (75% to 100%) with TextHandleMove haptics.
 * 3. Thermal safety monitor with dynamic temperature gauge and trigger threshold selector.
 * 4. Deep-discharge low battery warning system with customizable trigger points (15% vs 20%).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryProtectionScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BatteryViewModel = viewModel()
) {
    // Collect StateFlows safely with lifecycle awareness
    val isProtectionCapEnabled by viewModel.isProtectionCapEnabled.collectAsStateWithLifecycle()
    val chargeLimitTarget by viewModel.chargeLimitTarget.collectAsStateWithLifecycle()
    val isOverheatAlertEnabled by viewModel.isOverheatAlertEnabled.collectAsStateWithLifecycle()
    val overheatThreshold by viewModel.overheatTemperatureThreshold.collectAsStateWithLifecycle()
    val isLowBatteryAlertEnabled by viewModel.isLowBatteryAlertEnabled.collectAsStateWithLifecycle()
    val lowBatteryThreshold by viewModel.lowBatteryThreshold.collectAsStateWithLifecycle()
    val isOvernightTimerAlertEnabled by viewModel.isOvernightTimerAlertEnabled.collectAsStateWithLifecycle()
    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()

    // Support device hardware back press
    BackHandler(onBack = onNavigateBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Battery Protection",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Longevity & Thermal Safety Suite",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("protection_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================================================================
            // 1. PROTECTION STATUS CARD
            // ================================================================
            ProtectionStatusCard(
                isProtectionCapEnabled = isProtectionCapEnabled,
                chargeLimitTarget = chargeLimitTarget,
                batteryHealth = batteryState.healthStatus,
                voltageMilliVolts = batteryState.voltageMilliVolts,
                onProtectionCapToggle = { enabled ->
                    viewModel.setProtectionCapEnabled(enabled)
                }
            )

            // ================================================================
            // 2. CUSTOM CHARGE LIMIT SLIDER CARD
            // ================================================================
            CustomChargeLimitSliderCard(
                isProtectionCapEnabled = isProtectionCapEnabled,
                chargeLimitTarget = chargeLimitTarget,
                onChargeLimitChange = { newTarget ->
                    viewModel.setChargeLimitTarget(newTarget)
                    viewModel.setTargetChargePercentage(newTarget)
                }
            )

            // ================================================================
            // 3. THERMAL SAFETY SETTINGS CARD
            // ================================================================
            ThermalSafetySettingsCard(
                isOverheatAlertEnabled = isOverheatAlertEnabled,
                overheatThreshold = overheatThreshold,
                currentTemperatureCelsius = batteryState.temperatureCelsius,
                onToggleOverheatAlert = { enabled ->
                    viewModel.setOverheatAlertEnabled(enabled)
                },
                onThresholdChange = { threshold ->
                    viewModel.setOverheatTemperatureThreshold(threshold)
                }
            )

            // ================================================================
            // 4. LOW BATTERY WARNING CARD
            // ================================================================
            LowBatteryWarningCard(
                isLowBatteryAlertEnabled = isLowBatteryAlertEnabled,
                lowBatteryThreshold = lowBatteryThreshold,
                currentBatteryPercentage = batteryState.percentage,
                onToggleLowBatteryAlert = { enabled ->
                    viewModel.setLowBatteryAlertEnabled(enabled)
                },
                onThresholdChange = { threshold ->
                    viewModel.setLowBatteryThreshold(threshold)
                }
            )

            // Bottom safety margin for edge-to-edge navigation
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ============================================================================
// COMPONENT 1: Protection Status Card
// ============================================================================

@Composable
private fun ProtectionStatusCard(
    isProtectionCapEnabled: Boolean,
    chargeLimitTarget: Int,
    batteryHealth: String,
    voltageMilliVolts: Int,
    onProtectionCapToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val badgeColor = if (isProtectionCapEnabled) VoltGreen else MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBgColor = if (isProtectionCapEnabled) VoltGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("protection_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isProtectionCapEnabled) VoltGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isProtectionCapEnabled) VoltGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isProtectionCapEnabled) VoltGreen else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Battery Protection Cap",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isProtectionCapEnabled) "Active • Stress reduction enabled" else "Disabled • Full capacity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isProtectionCapEnabled,
                    onCheckedChange = { checked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onProtectionCapToggle(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoltGreen,
                        checkedTrackColor = VoltGreen.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("protection_cap_master_switch")
                )
            }

            // Visual Status Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBgColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("protection_mode_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isProtectionCapEnabled) Icons.Default.Check else Icons.Default.Info,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isProtectionCapEnabled) {
                            "Optimal Health: Capped at $chargeLimitTarget%"
                        } else {
                            "Standard Mode: Uncapped (100%)"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = badgeColor
                    )
                }
            }

            // Explanatory Micro-Copy
            Text(
                text = "Lithium-ion batteries experience exponential chemical degradation and mechanical stress above 4.15V (~80% state of charge). Capping daily charging at $chargeLimitTarget% reduces cathode crystal fracturing, slows electrolyte oxidation, and can extend overall battery cycle lifespan by up to 2.5×.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Health & Voltage Indicators
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusMetricPill(
                    icon = Icons.Default.Favorite,
                    label = "Health",
                    value = batteryHealth,
                    accentColor = VoltGreen
                )

                val voltageVolts = if (voltageMilliVolts > 0) String.format("%.2fV", voltageMilliVolts / 1000f) else "N/A"
                StatusMetricPill(
                    icon = Icons.Default.Bolt,
                    label = "Cell Voltage",
                    value = voltageVolts,
                    accentColor = VoltElectricCyan
                )

                val cycleExtension = when {
                    !isProtectionCapEnabled -> "1.0× (Baseline)"
                    chargeLimitTarget <= 80 -> "Up to 2.5×"
                    chargeLimitTarget <= 85 -> "Up to 2.0×"
                    chargeLimitTarget <= 90 -> "Up to 1.5×"
                    else -> "1.2×"
                }
                StatusMetricPill(
                    icon = Icons.Default.Shield,
                    label = "Lifespan Gain",
                    value = cycleExtension,
                    accentColor = if (isProtectionCapEnabled) VoltGreen else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ============================================================================
// COMPONENT 2: Custom Charge Limit Slider Card
// ============================================================================

@Composable
private fun CustomChargeLimitSliderCard(
    isProtectionCapEnabled: Boolean,
    chargeLimitTarget: Int,
    onChargeLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val snapPoints = remember { listOf(75, 80, 85, 90, 95, 100) }
    var lastHapticTarget by remember { mutableIntStateOf(chargeLimitTarget) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("charge_limit_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Charge Longevity Limit",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Threshold where alert chimes & limits charging",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("charge_limit_badge")
                ) {
                    Text(
                        text = "$chargeLimitTarget%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Material 3 Discrete Snap Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = chargeLimitTarget.toFloat(),
                    onValueChange = { floatValue ->
                        val snapped = ((floatValue + 2.5f) / 5f).toInt() * 5
                        val clamped = snapped.coerceIn(75, 100)
                        if (clamped != lastHapticTarget) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticTarget = clamped
                        }
                        onChargeLimitChange(clamped)
                    },
                    valueRange = 75f..100f,
                    steps = 4, // 80, 85, 90, 95 intermediate discrete steps
                    enabled = isProtectionCapEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        inactiveTickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("charge_limit_slider")
                )

                // Discrete Snap Point Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    snapPoints.forEach { point ->
                        val isSelected = point == chargeLimitTarget
                        Text(
                            text = "$point%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Segmented Snap Point Pill Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                snapPoints.forEach { point ->
                    val isSelected = point == chargeLimitTarget
                    val bg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        animationSpec = tween(150),
                        label = "pillBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(150),
                        label = "pillText"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                enabled = isProtectionCapEnabled
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticTarget = point
                                onChargeLimitChange(point)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$point%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor
                        )
                    }
                }
            }

            // Dynamic Longevity Insight Banner
            val (insightTitle, insightDesc) = when (chargeLimitTarget) {
                75 -> "Ultra-Longevity Tier" to "Recommended for kiosk, desktop docking, or navigation devices that remain plugged in continually."
                80 -> "Optimal Preservation Tier" to "Industry gold standard. Eliminates top-voltage battery strain while providing ample daily capacity."
                85 -> "Balanced Daily Tier" to "Recommended for power users needing longer runtime while still avoiding severe 100% saturation stress."
                90 -> "Extended Capacity Tier" to "Modest protection. Yields 90% run-time with partial cathode preservation."
                95 -> "Near-Full Capacity" to "Marginal degradation reduction compared to standard 100% charging."
                else -> "Full Capacity Mode" to "No cycle lifespan preservation. Focuses solely on maximum single-charge runtime."
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = insightTitle,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = insightDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// COMPONENT 3: Thermal Safety Settings Card
// ============================================================================

@Composable
private fun ThermalSafetySettingsCard(
    isOverheatAlertEnabled: Boolean,
    overheatThreshold: Float,
    currentTemperatureCelsius: Float,
    onToggleOverheatAlert: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val thresholds = remember { listOf(40.0f, 42.0f, 45.0f) }

    // Dynamic temperature status & gauge color
    val tempColor = when {
        currentTemperatureCelsius >= overheatThreshold -> VoltRed
        currentTemperatureCelsius >= 37.0f -> VoltAmber
        else -> VoltGreen
    }

    val tempStatusLabel = when {
        currentTemperatureCelsius >= overheatThreshold -> "Overheating!"
        currentTemperatureCelsius >= 37.0f -> "Warm"
        else -> "Normal"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("thermal_safety_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (currentTemperatureCelsius >= overheatThreshold && isOverheatAlertEnabled) {
                VoltRed.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoltRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = VoltRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Thermal Safety Alarm",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Audible alarm if charging heats battery cell",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isOverheatAlertEnabled,
                    onCheckedChange = { checked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleOverheatAlert(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoltRed,
                        checkedTrackColor = VoltRed.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("overheat_alarm_switch")
                )
            }

            // Live Temperature Gauge Chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tempColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, tempColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_temperature_gauge_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(tempColor)
                        )
                        Text(
                            text = "Current Battery Temp",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = String.format("%.1f°C", currentTemperatureCelsius),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = tempColor
                        )
                        Text(
                            text = "($tempStatusLabel)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tempColor
                        )
                    }
                }
            }

            // Segmented Threshold Chips (40°C, 42°C, 45°C)
            AnimatedVisibility(
                visible = isOverheatAlertEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Alarm Trigger Threshold",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        thresholds.forEach { threshold ->
                            val isSelected = overheatThreshold == threshold
                            val bg by animateColorAsState(
                                targetValue = if (isSelected) VoltRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                animationSpec = tween(150),
                                label = "tempThresholdBg"
                            )
                            val borderCol by animateColorAsState(
                                targetValue = if (isSelected) VoltRed else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                animationSpec = tween(150),
                                label = "tempThresholdBorder"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple()
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onThresholdChange(threshold)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${threshold.toInt()}°C",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (isSelected) VoltRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (threshold.toInt()) {
                                            40 -> "Conservative"
                                            42 -> "Recommended"
                                            else -> "High Heat"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) VoltRed else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "High temperatures above 42°C during high-wattage fast charging degrade the solid electrolyte interphase (SEI) layer, permanently reducing cell capacity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// COMPONENT 4: Low Battery Warning Card
// ============================================================================

@Composable
private fun LowBatteryWarningCard(
    isLowBatteryAlertEnabled: Boolean,
    lowBatteryThreshold: Int,
    currentBatteryPercentage: Int,
    onToggleLowBatteryAlert: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val lowThresholds = remember { listOf(15, 20) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("low_battery_warning_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoltAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = VoltAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Low Battery Warning",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Early alert to prevent deep discharge",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isLowBatteryAlertEnabled,
                    onCheckedChange = { checked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleLowBatteryAlert(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoltAmber,
                        checkedTrackColor = VoltAmber.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("low_battery_switch")
                )
            }

            // Explanatory Micro-Copy
            Text(
                text = "Deep discharge (dropping below 15-20%) subjects the anode to copper dissolution and dendrite short-circuits. Setting an early reminder ensures you plug in before critical voltage drop occurs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Threshold Selection (15% vs 20%)
            AnimatedVisibility(
                visible = isLowBatteryAlertEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Alert Threshold:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    lowThresholds.forEach { threshold ->
                        val isSelected = lowBatteryThreshold == threshold
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) VoltAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            animationSpec = tween(150),
                            label = "lowBatBg"
                        )
                        val borderCol by animateColorAsState(
                            targetValue = if (isSelected) VoltAmber else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            animationSpec = tween(150),
                            label = "lowBatBorder"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple()
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onThresholdChange(threshold)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$threshold%",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isSelected) VoltAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// HELPER: Status Metric Pill
// ============================================================================

@Composable
private fun StatusMetricPill(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
