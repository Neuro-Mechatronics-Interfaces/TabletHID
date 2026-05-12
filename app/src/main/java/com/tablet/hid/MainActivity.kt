package com.tablet.hid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.databinding.ActivityMainBinding
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.HidHostStore
import com.tablet.hid.util.HidPrefs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel: HidViewModel by viewModels()

    // ── Bluetooth enable launcher ────────────────────────────────────────────
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                Toast.makeText(this, "Bluetooth must be enabled to use TabletHID", Toast.LENGTH_LONG).show()
            }
        }

    // ── Permission launcher (API 31+) ────────────────────────────────────────
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = grants.values.all { it }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Bluetooth permissions are required for TabletHID to function.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                ensureBluetoothEnabled()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        if (AppearanceStore.isLargeText(newBase)) {
            config.fontScale = (config.fontScale * 1.18f).coerceAtLeast(1.18f)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (AppearanceStore.isHighContrast(this)) {
            setTheme(R.style.Theme_TabletHID_HighContrast)
        }
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide the FAB — not needed in this app.
        binding.fab.visibility = View.GONE

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        applyPendingStartMode(intent)
        checkAndRequestPermissions()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    supportActionBar?.subtitle = stateToStatusText(state)
                }
            }
        }
    }

    private fun stateToStatusText(state: BleHidManager.State): String = when (state) {
        is BleHidManager.State.Idle                -> ""
        is BleHidManager.State.Registering         -> "Starting…"
        is BleHidManager.State.WaitingForConnection -> "Waiting for connection"
        is BleHidManager.State.Reconnecting        -> "Connecting to ${state.deviceName}"
        is BleHidManager.State.PendingApproval    -> "Incoming host — open app to approve"
        is BleHidManager.State.Connected           ->
            "Connected · ${state.deviceName}"
        is BleHidManager.State.Error               -> "Error: ${state.message}"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            if (navController.currentDestination?.id != R.id.settingsFragment) {
                navController.navigate(R.id.action_global_to_settings)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyPendingStartMode(intent)
    }

    private fun applyPendingStartMode(intent: Intent?) {
        intent?.getStringExtra("start_mode")?.let { viewModel.pendingStartMode = it }
        if (intent?.getBooleanExtra("widget_reconnect", false) == true) {
            val lastAddress = HidPrefs.getLastDeviceAddress(this) ?: return
            viewModel.startServiceForMode(this, DeviceMode.TOUCH_MOUSE, lastAddress)
            viewModel.pendingStartMode = "touch_mouse"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // ── Permission & Bluetooth setup ─────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

            if (needed.isNotEmpty()) {
                requestPermissionsLauncher.launch(needed.toTypedArray())
            } else {
                ensureBluetoothEnabled()
            }
        } else {
            ensureBluetoothEnabled()
        }
    }

    private fun ensureBluetoothEnabled() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            Toast.makeText(this, "This device does not support Bluetooth.", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            @Suppress("DEPRECATION")
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        maybeAutoReconnect()
    }

    private fun maybeAutoReconnect() {
        if (!HidPrefs.isAutoReconnectEnabled(this)) return
        val lastAddress = HidPrefs.getLastDeviceAddress(this) ?: return
        val knownHosts = HidHostStore.getAll(this)
        if (knownHosts.none { it.address == lastAddress }) return
        // Only trigger when genuinely idle — activity can be recreated by orientation changes
        // (e.g. exiting Gamepad resets requestedOrientation), and we must not restart the
        // BLE stack while a connection is already live or in progress.
        val s = viewModel.state.value
        if (s !is BleHidManager.State.Idle && s !is BleHidManager.State.Error) return
        viewModel.startServiceForMode(this, DeviceMode.TOUCH_MOUSE, lastAddress)
    }
}
