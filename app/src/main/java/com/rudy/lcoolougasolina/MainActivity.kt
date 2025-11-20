package com.rudy.lcoolougasolina

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.MaterialTheme.typography
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Shapes
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import java.util.UUID
import androidx.core.content.edit

// -------------------- Models & Persistence --------------------
// Added field `thresholdUsed` to record the percentual used at save time.
// Default value kept to DEFAULT_THRESHOLD_PERCENT for backward compatibility.
data class Station(
    val id: String = UUID.randomUUID().toString(),
    var name: String? = null,
    var alcoholPrice: Double,
    var gasPrice: Double,
    var latitude: Double?,
    var longitude: Double?,
    var address: String? = null,
    var timestamp: Long = System.currentTimeMillis(),
    var thresholdUsed: Double = DEFAULT_THRESHOLD_PERCENT // <-- new field
)

private const val PREFS_NAME = "stations_prefs"
private const val PREFS_KEY_STATIONS = "stations_json"
private const val PREFS_KEY_THRESHOLD_CHECKED = "threshold_checked"
private const val DEFAULT_THRESHOLD_PERCENT = 70.0
private const val CHECKED_THRESHOLD_PERCENT = 75.0

fun loadStations(context: Context): MutableList<Station> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(PREFS_KEY_STATIONS, null) ?: return mutableListOf()
    return try {
        val type = object : TypeToken<MutableList<Station>>() {}.type
        Gson().fromJson<MutableList<Station>>(json, type) ?: mutableListOf()
    } catch (e: Exception) {
        e.printStackTrace()
        mutableListOf()
    }
}

fun saveStations(context: Context, list: List<Station>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(PREFS_KEY_STATIONS, Gson().toJson(list)) }
}

fun loadThresholdChecked(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PREFS_KEY_THRESHOLD_CHECKED, false)
}

fun saveThresholdChecked(context: Context, checked: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREFS_KEY_THRESHOLD_CHECKED, checked) }
}

// Recommendation logic: álcool vantajoso se (alcool/gas) * 100 <= thresholdPercent
fun computeRatioPercent(alcohol: Double, gas: Double): Double {
    if (gas == 0.0) return Double.POSITIVE_INFINITY
    return (alcohol / gas) * 100.0
}

fun recommendationText(ratioPercent: Double, thresholdPercent: Double = DEFAULT_THRESHOLD_PERCENT, ctx: Context? = null): String {
    // Use Context#getString when not inside a composable
    return if (ratioPercent <= thresholdPercent) ctx?.getString(R.string.resultado_use_alcool) ?: "USE ÁLCOOL"
    else ctx?.getString(R.string.resultado_use_gasolina) ?: "USE GASOLINA"
}

// Map selection holder (Compose-friendly, shared between activities)
object MapSelectionHolder {
    var tempLat: Double? by mutableStateOf(null)
    var tempLon: Double? by mutableStateOf(null)
    var tempAddress: String? by mutableStateOf(null)
}

// -------------------- Activity --------------------

class MainActivity : ComponentActivity() {

    private val mapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                data?.let {
                    val lat = it.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, Double.NaN)
                    val lon = it.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, Double.NaN)
                    val address = it.getStringExtra(MapPickerActivity.EXTRA_ADDRESS)
                    MapSelectionHolder.tempLat = if (!lat.isNaN()) lat else null
                    MapSelectionHolder.tempLon = if (!lon.isNaN()) lon else null
                    MapSelectionHolder.tempAddress = address
                }
            }
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { perms ->
            val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fine && coarse) openMapPicker() else
                Toast.makeText(this, getString(R.string.permission_denied_message), Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainScreen(onOpenMap = { requestLocationPermissionThenOpenMap() })
                }
            }
        }
    }

    private fun requestLocationPermissionThenOpenMap() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine && coarse) openMapPicker()
        else requestPermissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun openMapPicker() {
        val intent = Intent(this, MapPickerActivity::class.java)
        mapPickerLauncher.launch(intent)
    }
}

// -------------------- Themed UI --------------------

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColors(
        primary = Color(0xFFBB86FC),
        primaryVariant = Color(0xFF3700B3),
        secondary = Color(0xFF03DAC6),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onPrimary = Color.Black,
        onBackground = Color.White
    ) else lightColors(
        primary = Color(0xFF0066CC),
        primaryVariant = Color(0xFF004A99),
        secondary = Color(0xFF009688),
        background = Color(0xFFF6F8FB),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color.Black
    )

    MaterialTheme(colors = colors, typography = Typography(), shapes = Shapes(), content = content)
}

// -------------------- Main Screen --------------------

@SuppressLint("MutableCollectionMutableState")
@Composable
fun MainScreen(onOpenMap: () -> Unit) {
    val context = LocalContext.current

    // Form fields
    var name by remember { mutableStateOf("") }
    var alcoholText by remember { mutableStateOf("") }
    var gasText by remember { mutableStateOf("") }

    // threshold persisted properly (read once)
    val initialThresholdChecked = remember { loadThresholdChecked(context) }
    var thresholdChecked by rememberSaveable { mutableStateOf(initialThresholdChecked) }
    val thresholdPercent by remember(thresholdChecked) {
        derivedStateOf { if (thresholdChecked) CHECKED_THRESHOLD_PERCENT else DEFAULT_THRESHOLD_PERCENT }
    }

    // map selection
    val selLat = MapSelectionHolder.tempLat
    val selLon = MapSelectionHolder.tempLon
    val selAddress = MapSelectionHolder.tempAddress

    // stations list persisted (loaded once) - show most recent first
    var stations by remember { mutableStateOf(loadStations(context).sortedByDescending { it.timestamp }.toMutableList()) }

    // strings (usadas dentro do composable — ok)
    val title = stringResource(R.string.titulo_app)
    val stationNameLabel = stringResource(R.string.station_name_label)
    val alcoholLabel = stringResource(R.string.alcohol_price_label)
    val gasLabel = stringResource(R.string.gas_price_label)
    val btnSelectLocation = stringResource(R.string.btn_select_location)
    val btnSave = stringResource(R.string.btn_salvar)
    val locationNotSelectedStr = stringResource(R.string.location_not_selected_app)
    val msgInvalid = stringResource(R.string.invalid_values_message)
    val msgSaved = stringResource(R.string.saved_comparison)
    val unnamed = stringResource(R.string.unnamed_station)
    val tituloHistorico = stringResource(R.string.titulo_historico)
    val msgListaVazia = stringResource(R.string.msg_lista_vazia)

    // Use a single LazyColumn so the whole screen can scroll
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header card as first item
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                ) {
                    Text(text = title, style = typography.h6.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stationNameLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = alcoholText,
                            onValueChange = { alcoholText = it },
                            label = { Text(alcoholLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = gasText,
                            onValueChange = { gasText = it },
                            label = { Text(gasLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Switch(
                            checked = thresholdChecked,
                            onCheckedChange = { checked ->
                                thresholdChecked = checked
                                saveThresholdChecked(context, checked)
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(text = stringResource(R.string.lbl_criterio))

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${thresholdPercent.toInt()}%",
                            style = typography.body1.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Button centered below checkbox and inputs
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = onOpenMap,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .height(48.dp)
                                .widthIn(min = 220.dp)
                        ) {
                            Text(text = btnSelectLocation)
                        }
                    }

                    // selected address preview
                    Spacer(modifier = Modifier.height(12.dp))
                    if (selAddress != null || (selLat != null && selLon != null)) {
                        Text(
                            text = "${stringResource(R.string.selected_local)}: ${selAddress ?: "${selLat.format(6)}, ${selLon.format(6)}"}",
                            style = typography.body2
                        )
                    } else {
                        Text(text = locationNotSelectedStr, style = typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Save button
                    Button(
                        onClick = {
                            val a = alcoholText.toDoubleOrNull()
                            val g = gasText.toDoubleOrNull()
                            if (a == null || g == null) {
                                Toast.makeText(context, msgInvalid, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val station = Station(
                                name = name.ifBlank { null },
                                alcoholPrice = a,
                                gasPrice = g,
                                latitude = selLat,
                                longitude = selLon,
                                address = selAddress,
                                thresholdUsed = thresholdPercent // <-- save the threshold used at creation
                            )
                            stations = (stations + station).toMutableList()
                            // keep most recent first
                            stations = stations.sortedByDescending { it.timestamp }.toMutableList()
                            saveStations(context, stations)
                            // reset form
                            MapSelectionHolder.tempLat = null
                            MapSelectionHolder.tempLon = null
                            MapSelectionHolder.tempAddress = null
                            name = ""
                            alcoholText = ""
                            gasText = ""
                            Toast.makeText(context, "$msgSaved: ${station.name ?: unnamed}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(text = btnSave)
                    }
                }
            }
        }

        // Title for history
        item {
            Text(text = tituloHistorico, style = typography.subtitle1, modifier = Modifier.padding(vertical = 8.dp))
        }

        // Stations list (if empty, show a single item; else show the items)
        if (stations.isEmpty()) {
            item {
                Text(text = msgListaVazia, style = typography.body2)
            }
        } else {
            itemsIndexed(items = stations, key = { _, s -> s.id }) { _, station ->
                StationRow(
                    station = station,
                    // pass current UI threshold only for new comparisons; StationRow will use station.thresholdUsed for display
                    onEdit = { st ->
                        // populate fields for editing
                        name = st.name ?: ""
                        alcoholText = st.alcoholPrice.toString()
                        gasText = st.gasPrice.toString()
                        MapSelectionHolder.tempLat = st.latitude
                        MapSelectionHolder.tempLon = st.longitude
                        MapSelectionHolder.tempAddress = st.address
                        // remove the edited one
                        stations = stations.filter { it.id != st.id }.toMutableList()
                        saveStations(context, stations)
                    },
                    onDelete = { st ->
                        stations = stations.filter { it.id != st.id }.toMutableList()
                        saveStations(context, stations)
                    }
                )
            }
        }

        // bottom spacer so last item isn't glued to nav bars
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


@Composable
fun StationRow(
    station: Station,
    onEdit: (Station) -> Unit,
    onDelete: (Station) -> Unit
) {
    // <- IMPORTANT: all stringResource calls MUST be inside a composable
    val ctx = LocalContext.current
    val editCd = stringResource(R.string.cd_edit_station)
    val deleteCd = stringResource(R.string.cd_delete_station)
    val descEdit = stringResource(R.string.desc_editar)
    val descRemove = stringResource(R.string.desc_remover)

    val ratio = computeRatioPercent(station.alcoholPrice, station.gasPrice)

    // Now we use the threshold that was saved for THIS station.
    // station.thresholdUsed stores the percent used at creation/update time.
    val thresholdUsedForStation = station.thresholdUsed
    val rec = recommendationText(ratio, thresholdUsedForStation, ctx)

    // compute the formatted date string BEFORE invoking composable UI elements
    val dateStr: String? = remember(station.timestamp) {
        runCatching {
            val df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, Locale.getDefault())
            df.format(java.util.Date(station.timestamp))
        }.getOrNull()
    }

    // Colors for dark/light theme
    val isDark = isSystemInDarkTheme()
    val editColor = if (isDark) Color(0xFFFFE082) else Color(0xFFFFC107)
    val deleteColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)

    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name ?: stringResource(id = R.string.unnamed_station),
                    style = typography.subtitle1.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.label_prices, station.alcoholPrice, station.gasPrice),
                    style = typography.body2
                )

                val multiplier = if (ratio.isFinite()) ratio / 100.0 else Double.POSITIVE_INFINITY
                val thresholdUsedInt = thresholdUsedForStation.toInt()

                // Show the ratio and the multiplier
                Text(
                    text = stringResource(id = R.string.label_ratio_detail, ratio, multiplier),
                    style = typography.body2
                )

                // Show the threshold that was used when the station was saved and the recommendation based on that threshold
                Text(
                    text = stringResource(id = R.string.label_threshold_used, thresholdUsedInt, rec),
                    style = typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
                )

                // Show formatted date/time if available
                dateStr?.let { ds ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.label_date) + " " + ds, style = typography.caption)
                }

                station.address?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = it, style = typography.caption)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row {
                    Text(
                        text = descEdit,
                        color = editColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onEdit(station) }
                            .padding(6.dp)
                            .semantics {
                                contentDescription = editCd
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = descRemove,
                        color = deleteColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onDelete(station) }
                            .padding(6.dp)
                            .semantics {
                                contentDescription = deleteCd
                            }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

            }
        }
    }
}

/* Helpers */
private fun Double?.format(decimals: Int): String {
    return if (this == null) "N/A" else String.format(Locale.getDefault(), "%.${decimals}f", this)
}
