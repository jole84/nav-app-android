package com.jole84.navapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    @Volatile private var pageReady = false
    private val updateQueue = mutableListOf<Pair<Double, Double>>() // lat,lng buffer

    private val requestForegroundPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (fine) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                startLocationService()
            } else {
                showPermissionRationale()
            }
        }

    private val requestBackgroundPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // start anyway; foreground updates still work
            startLocationService()
        }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val lat = intent.getDoubleExtra("lat", Double.NaN)
            val lng = intent.getDoubleExtra("lng", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                runOnUiThread {
                    if (pageReady) {
                        webView.evaluateJavascript("window.onLocationUpdate(${lat}, ${lng});", null)
                    } else {
                        updateQueue.add(lat to lng)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // Add the JS bridge and load your hosted web app URL
        webView.addJavascriptInterface(WebAppBridge(), "AndroidBridge")
        webView.loadUrl("https://jole84.se/nav-app/index.html")

        registerReceiver(locationReceiver, IntentFilter("ACTION_LOCATION_UPDATED"))

        // request permissions up front
        requestForegroundPermissions.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationReceiver)
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationUpdatesService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopLocationService() {
        val i = Intent(this, LocationUpdatesService::class.java)
        stopService(i)
    }

    private fun flushQueueToWebView() {
        if (updateQueue.isEmpty()) return
        val copy = updateQueue.toList()
        updateQueue.clear()
        for ((lat, lng) in copy) {
            webView.evaluateJavascript("window.onLocationUpdate(${lat}, ${lng});", null)
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Location required")
            .setMessage("This app needs location permission to show your position on the map.")
            .setPositiveButton("Grant") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class WebAppBridge {
        @JavascriptInterface
        fun onWebviewReady() {
            runOnUiThread {
                val cur = webView.url ?: ""
                if (!cur.startsWith("https://jole84.se/nav-app")) return@runOnUiThread
                pageReady = true
                flushQueueToWebView()
            }
        }

        @JavascriptInterface
        fun startTracking() {
            runOnUiThread {
                requestForegroundPermissions.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }

        @JavascriptInterface
        fun stopTracking() {
            runOnUiThread { stopLocationService() }
        }
    }
}
