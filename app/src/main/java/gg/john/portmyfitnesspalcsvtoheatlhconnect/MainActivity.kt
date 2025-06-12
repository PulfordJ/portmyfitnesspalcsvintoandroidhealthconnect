package gg.john.portmyfitnesspalcsvtoheatlhconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import gg.john.portmyfitnesspalcsvtoheatlhconnect.ui.theme.PortmyfitnesspalcsvtoheatlhconnectTheme
import android.app.Activity
import android.content.Intent
import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gg.john.portmyfitnesspalcsvtoheatlhconnect.ui.theme.PortmyfitnesspalcsvtoheatlhconnectTheme
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.units.Mass

import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    // Define the Health Connect permissions you need
    private val PERMISSIONS = setOf(
        HealthPermission.getWritePermission(WeightRecord::class)
    )

    // Create the permissions launcher
    private val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()

    private lateinit var healthConnectClient: HealthConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        healthConnectClient = HealthConnectClient.getOrCreate(this)

        // State to track permission grant
        var permissionsGranted = false

        // Register for permission result
        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            permissionsGranted = granted.containsAll(PERMISSIONS)
            if (permissionsGranted) {
                // Now safe to show ImportCsvScreen
                setContent {
                    PortmyfitnesspalcsvtoheatlhconnectTheme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            ImportCsvScreen(Modifier.padding(innerPadding))
                        }
                    }
                }
            } else {
                // Optionally show a message or close the app
                val status = HealthConnectClient.getSdkStatus(applicationContext, "com.google.android.apps.healthdata")
                // TODO consider deleting this
                if (status == HealthConnectClient.SDK_AVAILABLE) {
                    Log.d("MainActivity", "Health Connect SDK AVALIABLE")
                } else {
                    Log.d("MainActivity", "Health Connect SDK UNAVALIABLE")
                }
                setContent {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Health Connect permissions are required to use this app.")
                    }
                }
            }
        }

        // Launch a coroutine to check permissions before showing UI
        lifecycleScope.launch {
            Log.d("MainActivity", "Checking Health Connect permissions...")
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            Log.d("MainActivity", "Granted permissions: $granted")
            if (granted.containsAll(PERMISSIONS)) {
                Log.i("MainActivity", "All required Health Connect permissions granted. Showing ImportCsvScreen.")
                setContent {
                    PortmyfitnesspalcsvtoheatlhconnectTheme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            ImportCsvScreen(Modifier.padding(innerPadding))
                        }
                    }
                }
            } else {

                Log.w("MainActivity", "Missing required Health Connect permissions. Requesting permissions from user.")
                // Request permissions
                requestPermissions.launch(PERMISSIONS)
            }
        }
    }
}



@Composable
fun ImportCsvScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var instructionsShown by remember { mutableStateOf(true) }
    var csvUri by remember { mutableStateOf<Uri?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var permissionsGranted by remember { mutableStateOf(false) }


    // Launcher for file picker
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            csvUri = uri
            instructionsShown = false
        }
    )

    // Get HealthConnectClient (safe to call multiple times)
    val healthConnectClient = remember { HealthConnectClient.getOrCreate(context) }

    Column(
        modifier = modifier
            .padding(24.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (instructionsShown) {
            Text(
                "Instructions:\n" +
                        "1. Log in to your MyFitnessPal account on a computer.\n" +
                        "2. Export your weight data as a CSV file from the 'Reports' section.\n" +
                        "3. Save the CSV file to your phone or cloud storage.\n" +
                        "4. Tap the button below to select your CSV file."
            )
            // TODO once selected, retry if the file cannot be parsed.
            Button(onClick = { csvPickerLauncher.launch(arrayOf("*/*")) }) {
                Text("Select MyFitnessPal CSV File")
            }
        } else {
                Text("CSV file selected! Tap below to import weights into Health Connect.")
                Button(onClick = {
                    csvUri?.let { uri ->
                        coroutineScope.launch {
                            importStatus = importWeightsFromCsv(context, uri, healthConnectClient)
                        }
                    }
                }) {
                    Text("Import Weights")
                }
                if (importStatus != null) {
                    Text(importStatus!!)

                }
        }
    }
}



suspend fun importWeightsFromCsv(
    context: android.content.Context,
    uri: Uri,
    healthConnectClient: HealthConnectClient
): String {
    val TAG = "ImportWeights"
    try {
        Log.d(TAG, "Opening input stream for URI: $uri")
        val inputStream =
            context.contentResolver.openInputStream(uri) ?: run {
                Log.e(TAG, "Failed to open file input stream for URI: $uri")
                return "Failed to open file."
            }
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()
        Log.d(TAG, "Read ${lines.size} lines from CSV file.")

        // CSV header: Date,Body Fat %,Weight
        val weightEntries = lines.drop(1).mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 3) {
                val dateStr = parts[0].trim()
                val weightStr = parts[2].trim()
                val weight = weightStr.toDoubleOrNull()
                if (weight != null && dateStr.isNotEmpty()) {
                    try {
                        val localDate = LocalDate.parse(dateStr)
                        val instant = localDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                        Log.d(TAG, "Parsed entry: date=$dateStr, weight=$weight")
                        WeightRecord(
                            weight = Mass.kilograms(weight),
                            time = instant,
                            zoneOffset = ZoneOffset.UTC,
                            // Use Metadata.manualEntry() because this weight entry is being ported/imported
                            // from old MyFitnessPal data, not automatically recorded by a device or sensor.
                            // This accurately reflects that the data was entered or migrated manually.
                            metadata = Metadata.manualEntry()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing date or creating WeightRecord for line: $line", e)
                        null
                    }
                } else {
                    Log.w(TAG, "Skipping line due to missing or invalid weight/date: $line")
                    null
                }
            } else {
                Log.w(TAG, "Skipping malformed line: $line")
                null
            }
        }
        reader.close()
        Log.i(TAG, "Prepared ${weightEntries.size} WeightRecord entries for insertion.")

        // Insert records into Health Connect
        healthConnectClient.insertRecords(weightEntries)
        Log.i(TAG, "Successfully inserted ${weightEntries.size} weight entries into Health Connect.")
        return "Imported ${weightEntries.size} weight entries into Health Connect."

    } catch (e: Exception) {
        Log.e(TAG, "Exception during CSV import", e)
        return "Error: ${e.message}"
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PortmyfitnesspalcsvtoheatlhconnectTheme {
        Greeting("Android")
    }
}