package gg.john.portmyfitnesspalcsvtohealthconnect

import android.os.Bundle
import android.util.Log
import android.net.Uri
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    private val PERMISSIONS = setOf(HealthPermission.getWritePermission(WeightRecord::class))
    private val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
    private lateinit var healthConnectClient: HealthConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        healthConnectClient = HealthConnectClient.getOrCreate(this)

        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(PERMISSIONS)) {
                showMainUI()
            } else {
                showPermissionDeniedMessage()
            }
        }

        lifecycleScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                showMainUI()
            } else {
                requestPermissions.launch(PERMISSIONS)
            }
        }
    }

    private fun showMainUI() {
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                ImportCsvScreen(Modifier.padding(innerPadding))
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Health Connect permissions are required to use this app.")
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

    val csvPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        csvUri = uri
        instructionsShown = false
    }

    val healthConnectClient = remember { HealthConnectClient.getOrCreate(context) }

    Column(
        modifier = modifier.padding(24.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (instructionsShown) {
            Text("""Instructions:
1. Export your weight data from MyFitnessPal as CSV.
2. Save the CSV to your phone.
3. Tap below to select it.""")
            Button(onClick = { csvPickerLauncher.launch(arrayOf("*/*")) }) {
                Text("Select CSV File")
            }
        } else {
            Text("CSV selected. Tap below to import to Health Connect.")
            Button(onClick = {
                csvUri?.let { uri ->
                    coroutineScope.launch {
                        importStatus = importWeightsFromCsv(context, uri, healthConnectClient)
                    }
                }
            }) {
                Text("Import Weights")
            }
            importStatus?.let { Text(it) }
        }
    }
}

suspend fun importWeightsFromCsv(
    context: Context,
    uri: Uri,
    healthConnectClient: HealthConnectClient
): String {
    val TAG = "ImportWeights"
    return try {
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri) ?: return "Failed to open file."))
        val lines = reader.readLines()
        val weightEntries = lines.drop(1).mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 3) {
                val dateStr = parts[0].trim()
                val weight = parts[2].trim().toDoubleOrNull()
                weight?.let {
                    try {
                        WeightRecord(
                            weight = Mass.kilograms(it),
                            time = LocalDate.parse(dateStr).atStartOfDay().toInstant(ZoneOffset.UTC),
                            zoneOffset = ZoneOffset.UTC,
                            metadata = Metadata.manualEntry()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Invalid date or record: $line", e)
                        null
                    }
                }
            } else null
        }
        reader.close()
        healthConnectClient.insertRecords(weightEntries)
        "Imported ${weightEntries.size} entries."
    } catch (e: Exception) {
        Log.e(TAG, "Exception during import", e)
        "Error: ${e.message}"
    }
}
