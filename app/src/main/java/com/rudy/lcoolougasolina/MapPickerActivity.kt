package com.rudy.lcoolougasolina

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import java.util.Locale
import java.util.concurrent.Executors

class MapPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ADDRESS = "extra_address"
    }

    private lateinit var mapView: MapView
    private var marker: Marker? = null
    private var selectedPoint: GeoPoint? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid configuration
        val ctx = applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        Configuration.getInstance().load(ctx, prefs)
        Configuration.getInstance().userAgentValue = packageName

        val root = FrameLayout(this)
        mapView = MapView(this)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(true)
        mapView.setUseDataConnection(true)

        root.addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Controls moved to TOP so they don't overlap zoom controls (zoom controls are bottom-right)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                setMargins(16, 32, 16, 16)
            }
            layoutParams = params
        }

        val btnUseMyLocation = Button(this).apply {
            text = getString(R.string.get_location_button)
            setOnClickListener { centerOnMyLocation() }
        }

        val btnConfirm = Button(this).apply {
            text = getString(R.string.confirm_selection_button)
            setOnClickListener { confirmSelectionAndFinish() }
        }

        controls.addView(btnUseMyLocation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(btnConfirm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(controls)
        setContentView(root)

        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(GeoPoint(-3.7327, -38.5267)) // fallback

        val receiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                p?.let { placeMarkerAt(it) }
                return true
            }
            override fun longPressHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                p?.let { placeMarkerAt(it) }
                return true
            }
        }
        mapView.overlays.add(org.osmdroid.views.overlay.MapEventsOverlay(receiver))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            centerOnMyLocation()
        }
    }

    private fun placeMarkerAt(p: GeoPoint) {
        selectedPoint = p
        runOnUiThread {
            if (marker == null) {
                marker = Marker(mapView)
                marker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker!!.title = getString(R.string.selection_marker_title)
                mapView.overlays.add(marker)
            }
            marker!!.position = p
            mapView.controller.animateTo(p)
            mapView.invalidate()
        }
    }

    private fun confirmSelectionAndFinish() {
        val p = selectedPoint
        if (p == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Reverse geocode address in background
        val lat = p.latitude
        val lon = p.longitude
        executor.execute {
            var addressText: String? = null
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val list = geocoder.getFromLocation(lat, lon, 1)
                if (!list.isNullOrEmpty()) {
                    val addr = list[0]
                    val lines = (0..addr.maxAddressLineIndex).mapNotNull { i ->
                        addr.getAddressLine(i)
                    }
                    addressText = lines.joinToString(", ")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Return result on UI thread
                runOnUiThread {
                    val intent = Intent().apply {
                        putExtra(EXTRA_LATITUDE, lat)
                        putExtra(EXTRA_LONGITUDE, lon)
                        putExtra(EXTRA_ADDRESS, addressText)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun centerOnMyLocation() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setMaxUpdates(1)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location? = result.lastLocation
                loc?.let {
                    val gp = GeoPoint(it.latitude, it.longitude)
                    mapView.controller.setZoom(16.0)
                    mapView.controller.animateTo(gp)
                    placeMarkerAt(gp)
                }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
        mapView.onDetach()
    }
}
