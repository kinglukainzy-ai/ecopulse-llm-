package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.EcoPulseApp
import com.example.ui.EcoPulseViewModel
import com.example.ui.theme.EcoPulseTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest

class MainActivity : ComponentActivity() {
    private val viewModel: EcoPulseViewModel by viewModels()

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Gets a single current location fix and forwards it to the ViewModel,
     * which calls the bot server's live /weather endpoint to populate a real
     * hazard alert. Called once permission is confirmed granted.
     */
    private fun fetchLocationAndLoadAlert() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()
        try {
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.fetchLiveLocationAlert(location.latitude, location.longitude)
                    }
                }
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call - safe to ignore,
            // the feed simply stays empty until the user grants permission again.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (granted) {
                    fetchLocationAndLoadAlert()
                }
            }

            // Request location once per app launch, right at startup.
            LaunchedEffect(Unit) {
                if (hasLocationPermission()) {
                    fetchLocationAndLoadAlert()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            EcoPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFFFBFDF8)
                ) {
                    EcoPulseApp(viewModel)
                }
            }
        }
    }
}
