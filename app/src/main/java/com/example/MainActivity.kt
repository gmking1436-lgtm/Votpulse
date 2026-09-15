package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BatteryDashboardScreen
import com.example.ui.touch.LowLatencyWindowEffect
import com.example.ui.touch.TouchOptimizationHelper
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemeManager
import com.example.ui.theme.VoltElectricCyan
import com.example.ui.theme.VoltPulseTheme

/**
 * Main application entry point for VoltPulse.
 *
 * Implements:
 * - Edge-to-edge layout styling with dark canvas support.
 * - Runtime Notification Permission acquisition ([Manifest.permission.POST_NOTIFICATIONS]) for Android 13+ (API 33+).
 * - Automatic Battery Optimization check ([PowerManager.isIgnoringBatteryOptimizations]) with guided M3 user prompt.
 * - Activity lifecycle observation pausing UI animations during background transitions.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TouchOptimizationHelper.configureLowLatencyWindow(window)

        setContent {
            LowLatencyWindowEffect()
            val themeManager = remember { ThemeManager.getInstance(applicationContext) }
            val currentThemeMode by themeManager.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.AMOLED_PITCH_BLACK
            )

            VoltPulseTheme(themeMode = currentThemeMode) {
                // Request Notification Permission on Android 13+ (API 33+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    Log.d(TAG, "Notification permission result: $isGranted")
                }

                // Launcher for Battery Optimization Exemption intent
                val batteryOptLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    Log.d(TAG, "Returned from battery optimization settings")
                }

                var showBatteryOptDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current

                // Check permissions on startup
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotificationPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasNotificationPermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Check if battery optimization is already disabled for uninterrupted background triggers
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        val isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                        if (!isIgnoringOptimizations) {
                            showBatteryOptDialog = true
                        }
                    }
                }

                // Re-check optimization state when returning to foreground
                DisposableEffect(this@MainActivity) {
                    val observer = object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                    showBatteryOptDialog = false
                                }
                            }
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                // Battery Optimization Exemption Dialog
                if (showBatteryOptDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryOptDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = VoltElectricCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.battery_opt_dialog_title),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.battery_opt_dialog_desc),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showBatteryOptDialog = false
                                    requestIgnoreBatteryOptimization(context, batteryOptLauncher)
                                }
                            ) {
                                Text(stringResource(R.string.battery_opt_allow))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatteryOptDialog = false }) {
                                Text(stringResource(R.string.battery_opt_later))
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryDashboardScreen()
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimization(
        context: Context,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                launcher.launch(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Direct ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, opening settings page", e)
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    launcher.launch(fallbackIntent)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Could not open battery optimization settings", fallbackEx)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

/**
 * Maintained for unit & screenshot regression test compatibility.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
