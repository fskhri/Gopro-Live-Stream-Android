package com.gopro.rebuild

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gopro.rebuild.ui.screens.*
import com.gopro.rebuild.ui.theme.GoProTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "Permissions needed: ${denied.keys.joinToString()}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContent {
            GoProTheme {
                GoProNavigation()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun GoProNavigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val debugPreview = java.io.File(
        context.getExternalFilesDir(null), "debug_local.ts"
    ).exists()
    val start = if (debugPreview) "preview" else "scan"

    NavHost(navController = navController, startDestination = start) {

        composable("scan") {
            ScanScreen(
                onCameraConnected = {
                    navController.navigate("control") {
                        popUpTo("scan") { inclusive = true }
                    }
                }
            )
        }

        composable("control") {
            ControlScreen(
                onNavigateToMedia = { navController.navigate("media") },
                onNavigateToPreview = { navController.navigate("preview") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPresets = { navController.navigate("presets") },
                onNavigateToCameraInfo = { navController.navigate("camera_info") },
                onNavigateToTiktok = { navController.navigate("tiktok_stream") },
                onDisconnect = {
                    navController.navigate("scan") {
                        popUpTo("control") { inclusive = true }
                    }
                }
            )
        }

        composable("tiktok_stream") {
            TiktokStreamScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate("tiktok_login") }
            )
        }

        composable("tiktok_login") {
            TiktokLoginScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("media") {
            MediaScreen(onBack = { navController.popBackStack() })
        }

        composable("preview") {
            LivePreviewScreen(onBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("presets") {
            PresetScreen(onBack = { navController.popBackStack() })
        }

        composable("camera_info") {
            CameraInfoScreen(onBack = { navController.popBackStack() })
        }
    }
}
