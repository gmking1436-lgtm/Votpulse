package com.example.ui

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.display.DynamicDisplayRefreshEffect
import com.example.display.rememberDynamicRefreshRateState
import com.example.display.trackUserInteraction
import com.example.ui.components.ChargingAnalyticsChart
import com.example.ui.components.LiquidWaveBatteryGauge
import com.example.ui.components.ProtectionSettingsCard
import com.example.ui.components.ThemeSelectorBottomSheet
import com.example.ui.theme.AppThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.BatteryInfoModel
import com.example.ui.theme.VoltAmber
import com.example.ui.theme.VoltElectricCyan
import com.example.ui.theme.VoltGreen
import com.example.ui.theme.VoltPulseTheme
import com.example.ui.theme.VoltRed

/**
 * Main Battery Dashboard Screen.
 *
 * Implements:
 * - Material 3 Scaffold with unified OLED dark canvas
 * - Responsive 60fps animated circular battery indicator
 * - Dynamic color adaptation based on percentage and charging state
 * - Pulsing charging animation with lifecycle observation to halt animations in onStop
 * - Comprehensive hardware telemetry card grid
 * - Direct integration with [SoundSettingsSection]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: BatteryViewModel = viewModel()
) {
    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()
    val isPluggedEnabled by viewModel.isPluggedAlertEnabled.collectAsStateWithLifecycle()
    val isUnpluggedEnabled by viewModel.isUnpluggedAlertEnabled.collectAsStateWithLifecycle()
    val isFullChargeEnabled by viewModel.isFullChargeAlertEnabled.collectAsStateWithLifecycle()
    val pluggedUri by viewModel.pluggedSoundUri.collectAsStateWithLifecycle()
    val unpluggedUri by viewModel.unpluggedSoundUri.collectAsStateWithLifecycle()
    val fullChargeUri by viewModel.fullChargeSoundUri.collectAsStateWithLifecycle()
    val currentPlayingPreview by viewModel.currentPlayingPreview.collectAsStateWithLifecycle()
    val currentThemeMode by viewModel.selectedThemeMode.collectAsStateWithLifecycle()

    val isProtectionCapEnabled by viewModel.isProtectionCapEnabled.collectAsStateWithLifecycle()
    val targetPercentage by viewModel.targetChargePercentage.collectAsStateWithLifecycle()
    val isLowBatteryAlertEnabled by viewModel.isLowBatteryAlertEnabled.collectAsStateWithLifecycle()
    val lowBatteryThreshold by viewModel.lowBatteryThreshold.collectAsStateWithLifecycle()
    val isOvernightTimerAlertEnabled by viewModel.isOvernightTimerAlertEnabled.collectAsStateWithLifecycle()
    val isOverheatAlertEnabled by viewModel.isOverheatAlertEnabled.collectAsStateWithLifecycle()
    val overheatThresholdCelsius by viewModel.overheatTemperatureThreshold.collectAsStateWithLifecycle()
    val chargingTelemetry by viewModel.chargingTelemetry.collectAsStateWithLifecycle()
    val chargingSessionPoints by viewModel.chargingSessionPoints.collectAsStateWithLifecycle()
    val isVoiceAnnouncementEnabled by viewModel.isVoiceAnnouncementEnabled.collectAsStateWithLifecycle()

    var showThemeSelector by remember { mutableStateOf(false) }

    // Lifecycle monitoring: stop animation loops when activity is stopped
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleActive by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleActive = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Display Refresh Rate & Variable Refresh Rate (120Hz/90Hz/60Hz/30Hz) Management
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isPowerSaveMode = powerManager?.isPowerSaveMode == true

    val listState = rememberLazyListState()
    var userTouchTimestamp by remember { mutableLongStateOf(0L) }
    val refreshRateState = rememberDynamicRefreshRateState()

    // Dynamic Display Refresh Rate Arbiter: elevates to 120Hz on interaction, drops to 30Hz/60Hz on idle
    DynamicDisplayRefreshEffect(
        isUserInteracting = System.currentTimeMillis() - userTouchTimestamp < 1500L,
        isScrolling = listState.isScrollInProgress,
        isAnimationRunning = isLifecycleActive,
        batteryPercentage = batteryState.percentage,
        isCharging = batteryState.isCharging,
        refreshState = refreshRateState
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .trackUserInteraction {
                userTouchTimestamp = System.currentTimeMillis()
            },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VoltPulse",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (batteryState.isCharging) "Charging Active" else "Battery Standby",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (batteryState.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val effectiveFps = refreshRateState.currentFps.toInt()
                                if (effectiveFps > 0) {
                                    Text(
                                        text = "• ${effectiveFps}Hz",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (effectiveFps >= 90) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showThemeSelector = true },
                        modifier = Modifier.testTag("theme_selector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Select Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Animated Futuristic Liquid Wave Battery Gauge
                LiquidWaveBatteryGauge(
                    percentage = batteryState.percentage,
                    isCharging = batteryState.isCharging,
                    voltageMilliVolts = batteryState.voltageMilliVolts,
                    temperatureCelsius = batteryState.temperatureCelsius,
                    healthStatus = batteryState.healthStatus,
                    isAnimationActive = isLifecycleActive,
                    isPowerSaveMode = isPowerSaveMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Diagnostics telemetry card grid
                BatteryTelemetryGrid(
                    batteryInfo = batteryState,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Custom Canvas Charging Analytics Curve & Protection Graph
                ChargingAnalyticsChart(
                    dataPoints = chargingSessionPoints,
                    targetPercentage = targetPercentage,
                    isCharging = batteryState.isCharging,
                    currentWattage = chargingTelemetry.watts,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Advanced Battery Longevity and Hardware Protection Card
                ProtectionSettingsCard(
                    isProtectionCapEnabled = isProtectionCapEnabled,
                    onProtectionCapEnabledChange = viewModel::setProtectionCapEnabled,
                    targetPercentage = targetPercentage,
                    onTargetPercentageChange = viewModel::setChargeLimitTarget,
                    isLowBatteryAlertEnabled = isLowBatteryAlertEnabled,
                    onLowBatteryAlertEnabledChange = viewModel::setLowBatteryAlertEnabled,
                    lowBatteryThreshold = lowBatteryThreshold,
                    onLowBatteryThresholdChange = viewModel::setLowBatteryThreshold,
                    isOvernightTimerAlertEnabled = isOvernightTimerAlertEnabled,
                    onOvernightTimerAlertEnabledChange = viewModel::setOvernightTimerAlertEnabled,
                    isOverheatAlertEnabled = isOverheatAlertEnabled,
                    onOverheatAlertEnabledChange = viewModel::setOverheatAlertEnabled,
                    overheatThresholdCelsius = overheatThresholdCelsius,
                    onOverheatThresholdChange = viewModel::setOverheatTemperatureThreshold,
                    currentTemperatureCelsius = batteryState.temperatureCelsius,
                    telemetry = chargingTelemetry,
                    batteryHealth = batteryState.healthStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Sound and alert configuration section
                SoundSettingsSection(
                    isPluggedEnabled = isPluggedEnabled,
                    onPluggedEnabledChange = viewModel::setPluggedAlertEnabled,
                    pluggedUri = pluggedUri,
                    onPluggedUriSelected = { uri: Uri? -> viewModel.setPluggedSoundUri(uri) },
                    isUnpluggedEnabled = isUnpluggedEnabled,
                    onUnpluggedEnabledChange = viewModel::setUnpluggedAlertEnabled,
                    unpluggedUri = unpluggedUri,
                    onUnpluggedUriSelected = { uri: Uri? -> viewModel.setUnpluggedSoundUri(uri) },
                    isFullChargeEnabled = isFullChargeEnabled,
                    onFullChargeEnabledChange = viewModel::setFullChargeAlertEnabled,
                    fullChargeUri = fullChargeUri,
                    onFullChargeUriSelected = { uri: Uri? -> viewModel.setFullChargeSoundUri(uri) },
                    isVoiceAnnouncementEnabled = isVoiceAnnouncementEnabled,
                    onVoiceAnnouncementEnabledChange = viewModel::setVoiceAnnouncementEnabled,
                    onTestVoiceAnnouncement = viewModel::testVoiceAnnouncement,
                    currentPlayingPreview = currentPlayingPreview,
                    onTogglePreview = viewModel::togglePreview,
                    onStopPreview = viewModel::stopPreview,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        if (showThemeSelector) {
            ThemeSelectorBottomSheet(
                currentTheme = currentThemeMode,
                onThemeSelected = { newTheme ->
                    viewModel.setThemeMode(newTheme)
                    showThemeSelector = false
                },
                onDismissRequest = {
                    showThemeSelector = false
                }
            )
        }
    }
}

/**
 * High-performance circular battery indicator with dynamic color mapping and pulsing halo.
 */
@Composable
fun CircularBatteryIndicator(
    percentage: Int,
    isCharging: Boolean,
    isAnimationActive: Boolean,
    modifier: Modifier = Modifier
) {
    // 1. Dynamic Color Adaptation:
    // Green for healthy/high (>50%), Yellow for medium (21-50%), Red for low (<=20%), Blue/Teal for charging
    val targetColor = when {
        isCharging -> VoltElectricCyan
        percentage > 50 -> VoltGreen
        percentage > 20 -> VoltAmber
        else -> VoltRed
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 600),
        label = "batteryGaugeColor"
    )

    // 2. Smooth Arc Progress Animation
    val targetProgress = (percentage.coerceIn(0, 100)) / 100f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "batteryProgress"
    )

    // 3. Pulsing Charging Glow (only runs when actively charging & lifecycle is active)
    val infiniteTransition = rememberInfiniteTransition(label = "pulseLoop")
    val pulseScale by if (isCharging && isAnimationActive) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val pulseAlpha by if (isCharging && isAnimationActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        remember { mutableStateOf(0.0f) }
    }

    Box(
        modifier = modifier
            .padding(vertical = 12.dp)
            .testTag("battery_indicator"),
        contentAlignment = Alignment.Center
    ) {
        // Charging Pulse Halo
        if (isCharging) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(animatedColor.copy(alpha = pulseAlpha))
            )
        }

        // Circular Canvas Gauge
        Canvas(modifier = Modifier.size(210.dp)) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeftOffset = strokeWidth / 2

            // Track background circle
            drawArc(
                color = Color(0xFF1E2634),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Dynamic Progress Arc with Neon Glow
            if (animatedProgress > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to animatedColor.copy(alpha = 0.7f),
                        animatedProgress to animatedColor,
                        1.0f to animatedColor
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center Metric Information
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Charging",
                    tint = animatedColor,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(bottom = 2.dp)
                )
            }

            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            val statusLabel = when {
                percentage >= 100 && isCharging -> "FULL CHARGED"
                isCharging -> "CHARGING"
                else -> "DISCHARGING"
            }

            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(animatedColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = animatedColor
                )
            }
        }
    }
}

/**
 * Diagnostics card grid displaying key battery hardware telemetry.
 */
@Composable
fun BatteryTelemetryGrid(
    batteryInfo: BatteryInfoModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "HARDWARE DIAGNOSTICS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // 2-Column Row 1: Health & Temperature
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                title = "Health",
                value = batteryInfo.healthStatus,
                icon = Icons.Default.Favorite,
                accentColor = if (batteryInfo.healthStatus.equals("Good", ignoreCase = true)) VoltGreen else VoltAmber,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_health"
            )
            TelemetryMetricCard(
                title = "Temperature",
                value = "${batteryInfo.temperatureCelsius} °C",
                icon = Icons.Default.Thermostat,
                accentColor = if (batteryInfo.temperatureCelsius > 42f) VoltRed else VoltElectricCyan,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_temp"
            )
        }

        // 2-Column Row 2: Voltage & Battery Tech
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val formattedVoltage = if (batteryInfo.voltageMilliVolts > 0) {
                String.format(java.util.Locale.US, "%.2f V", batteryInfo.voltageMilliVolts / 1000f)
            } else {
                "-- V"
            }

            TelemetryMetricCard(
                title = "Voltage",
                value = formattedVoltage,
                icon = Icons.Default.ElectricMeter,
                accentColor = VoltAmber,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_voltage"
            )
            TelemetryMetricCard(
                title = "Technology",
                value = batteryInfo.technology,
                icon = Icons.Default.Memory,
                accentColor = VoltElectricCyan,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_tech"
            )
        }

        // Full-width Row 3: Last Full Charge Timestamp
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("telemetry_last_full_charge"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoltGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = VoltGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Last 100% Full Charge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = batteryInfo.lastFullChargeFormatted,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BatteryDashboardScreenPreview() {
    VoltPulseTheme {
        CircularBatteryIndicator(
            percentage = 85,
            isCharging = true,
            isAnimationActive = true
        )
    }
}
