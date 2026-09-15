package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.CardGlowCyan
import com.example.ui.theme.CardGlowRed
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceBorder
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VoltAmber
import com.example.ui.theme.VoltElectricCyan
import com.example.ui.theme.VoltGreen
import com.example.ui.theme.VoltRed
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoltPulseScreen(
    viewModel: VoltPulseViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(VoltElectricCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "VoltPulse Logo",
                                tint = VoltElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "VoltPulse",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // Battery mode badge
                    val badgeColor = if (uiState.batteryState.isCharging) VoltGreen else VoltElectricCyan
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.batteryState.isCharging) "CHARGING" else "OPTIMIZED",
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Persistent Alarm Ringing Warning Banner
            item {
                AnimatedVisibility(
                    visible = uiState.isAlarmRinging,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AlarmRingingBanner(
                        batteryPercent = uiState.batteryState.percentage,
                        onDismiss = { viewModel.dismissAlarm() }
                    )
                }
            }

            // Central Pulse Battery Gauge
            item {
                BatteryPulseGauge(
                    batteryPercent = uiState.batteryState.percentage,
                    isCharging = uiState.batteryState.isCharging,
                    statusText = uiState.batteryState.statusDescription,
                    targetPercent = uiState.targetChargePercent
                )
            }

            // Quick Actions / Test Alarm
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isAlarmRinging) {
                        Button(
                            onClick = { viewModel.dismissAlarm() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("dismiss_alarm_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoltRed,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dismiss Alarm", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.triggerTestAlarm() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("test_alarm_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = VoltElectricCyan
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(VoltElectricCyan, VoltGreen))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Alarm", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Full Charge Alarm Configuration Card
            item {
                FullChargeAlarmCard(
                    enabled = uiState.fullChargeAlarmEnabled,
                    onEnabledChange = { viewModel.setFullChargeAlarmEnabled(it) },
                    targetPercent = uiState.targetChargePercent,
                    onTargetPercentChange = { viewModel.setTargetChargePercent(it) },
                    soundEnabled = uiState.soundEnabled,
                    onSoundEnabledChange = { viewModel.setSoundEnabled(it) },
                    vibrateEnabled = uiState.vibrateEnabled,
                    onVibrateEnabledChange = { viewModel.setVibrateEnabled(it) }
                )
            }

            // Hardware Power Connection Triggers Card
            item {
                PowerTriggersCard(
                    connectAlert = uiState.powerConnectedAlert,
                    onConnectAlertChange = { viewModel.setPowerConnectedAlert(it) },
                    disconnectAlert = uiState.powerDisconnectedAlert,
                    onDisconnectAlertChange = { viewModel.setPowerDisconnectedAlert(it) }
                )
            }

            // Battery Telemetry & Diagnostics Card
            item {
                BatteryTelemetryCard(
                    voltageMv = uiState.batteryState.voltageMv,
                    temperatureC = uiState.batteryState.temperatureCelsius,
                    technology = uiState.batteryState.technology,
                    health = uiState.batteryState.health.name
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun AlarmRingingBanner(
    batteryPercent: Int,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp)
            .border(2.dp, VoltRed, RoundedCornerShape(18.dp))
            .testTag("alarm_active_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardGlowRed
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = VoltRed,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "TARGET CHARGE REACHED ($batteryPercent%)",
                    color = VoltRed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "Unplug your charger to protect battery cycle life and prevent heat degradation.",
                color = TextPrimary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltRed,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("banner_dismiss_button")
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DISMISS CONTINUOUS ALARM", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BatteryPulseGauge(
    batteryPercent: Int,
    isCharging: Boolean,
    statusText: String,
    targetPercent: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            batteryPercent >= targetPercent -> VoltGreen
            batteryPercent <= 20 -> VoltRed
            isCharging -> VoltElectricCyan
            else -> VoltAmber
        },
        label = "ring_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(ObsidianSurfaceBorder, ringColor.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Background Track and animated charging sweep
                Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    val arcSize = Size(diameter, diameter)

                    // Track
                    drawArc(
                        color = Color(0xFF1E2838),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Target indicator tick
                    val progressSweep = (270f * (batteryPercent.coerceIn(0, 100) / 100f))

                    // Active Charge Ring
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                ringColor.copy(alpha = 0.7f),
                                ringColor,
                                if (isCharging) VoltElectricCyan.copy(alpha = pulseAlpha) else ringColor
                            )
                        ),
                        startAngle = 135f,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Center info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isCharging) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Charging Bolt",
                            tint = VoltElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = ringColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "$batteryPercent%",
                        color = TextPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )

                    Text(
                        text = "Target: $targetPercent%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (isCharging) "Power connected • Safe monitoring active" else "Discharging • Standby battery optimization",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun FullChargeAlarmCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    targetPercent: Int,
    onTargetPercentChange: (Int) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    vibrateEnabled: Boolean,
    onVibrateEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(ObsidianSurfaceBorder, ObsidianSurfaceVariant)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VoltGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = VoltGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Full-Charge Alarm",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Continuous alerting on target battery level",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("full_charge_alarm_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoltGreen,
                        checkedTrackColor = VoltGreen.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = ObsidianSurfaceVariant
                    )
                )
            }

            // Target charge level slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceVariant.copy(alpha = 0.6f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target Charge Alarm Threshold",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$targetPercent%",
                        color = VoltElectricCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = targetPercent.toFloat(),
                    onValueChange = { onTargetPercentChange(it.roundToInt()) },
                    valueRange = 80f..100f,
                    steps = 19,
                    enabled = enabled,
                    modifier = Modifier.testTag("alarm_threshold_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = VoltElectricCyan,
                        activeTrackColor = VoltElectricCyan,
                        inactiveTrackColor = ObsidianSurfaceBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("80% (Preserve cycle)", color = TextTertiary, fontSize = 11.sp)
                    Text("100% (Maximum)", color = TextTertiary, fontSize = 11.sp)
                }
            }

            // Sound and Vibrate controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ObsidianSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = VoltElectricCyan, modifier = Modifier.size(18.dp))
                            Text("Sound", color = TextPrimary, fontSize = 13.sp)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = onSoundEnabledChange,
                            enabled = enabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VoltElectricCyan,
                                checkedTrackColor = VoltElectricCyan.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ObsidianSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = VoltAmber, modifier = Modifier.size(18.dp))
                            Text("Vibrate", color = TextPrimary, fontSize = 13.sp)
                        }
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = onVibrateEnabledChange,
                            enabled = enabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VoltAmber,
                                checkedTrackColor = VoltAmber.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PowerTriggersCard(
    connectAlert: Boolean,
    onConnectAlertChange: (Boolean) -> Unit,
    disconnectAlert: Boolean,
    onDisconnectAlertChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(ObsidianSurfaceBorder, ObsidianSurfaceVariant)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Hardware Power State Broadcasts",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Power, contentDescription = null, tint = VoltGreen, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Charger Plugged Alert", color = TextPrimary, fontSize = 14.sp)
                        Text("ACTION_POWER_CONNECTED broadcast", color = TextTertiary, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = connectAlert,
                    onCheckedChange = onConnectAlertChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = VoltGreen)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PowerOff, contentDescription = null, tint = VoltAmber, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Charger Unplugged Alert", color = TextPrimary, fontSize = 14.sp)
                        Text("ACTION_POWER_DISCONNECTED broadcast", color = TextTertiary, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = disconnectAlert,
                    onCheckedChange = onDisconnectAlertChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = VoltAmber)
                )
            }
        }
    }
}

@Composable
fun BatteryTelemetryCard(
    voltageMv: Int,
    temperatureC: Float,
    technology: String,
    health: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(ObsidianSurfaceBorder, ObsidianSurfaceVariant)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Live Battery Telemetry",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryItem(
                    title = "Voltage",
                    value = if (voltageMv > 0) "%.2f V".format(voltageMv / 1000.0) else "N/A",
                    detail = "$voltageMv mV",
                    icon = Icons.Default.Bolt,
                    tint = VoltElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryItem(
                    title = "Temperature",
                    value = "%.1f °C".format(temperatureC),
                    detail = if (temperatureC < 38f) "Normal" else "Elevated",
                    icon = Icons.Default.Thermostat,
                    tint = if (temperatureC < 38f) VoltGreen else VoltRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryItem(
                    title = "Condition",
                    value = health,
                    detail = "Chemistry $technology",
                    icon = Icons.Default.CheckCircle,
                    tint = VoltGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryItem(
                    title = "Receiver State",
                    value = "Active",
                    detail = "Static Broadcasts",
                    icon = Icons.Default.NotificationsActive,
                    tint = VoltElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TelemetryItem(
    title: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(text = title, color = TextSecondary, fontSize = 12.sp)
            }
            Text(text = value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = detail, color = TextTertiary, fontSize = 11.sp)
        }
    }
}
