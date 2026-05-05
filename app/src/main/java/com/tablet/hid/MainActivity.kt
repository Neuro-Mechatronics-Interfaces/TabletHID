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
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.tablet.hid.databinding.ActivityMainBinding
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.LoggingStore
import com.tablet.hid.util.OrientationStore

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

        checkAndRequestPermissions()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            showSettingsDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val dp = resources.displayMetrics.density
        val dp4  = (4  * dp).toInt()
        val dp8  = (8  * dp).toInt()
        val dp12 = (12 * dp).toInt()
        val dp16 = (16 * dp).toInt()

        var selectedAppearance = AppearanceStore.get(this)
        val originalLargeText = AppearanceStore.isLargeText(this)
        val originalHighContrast = AppearanceStore.isHighContrast(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp4, dp16, 0)
        }

        // ── Bluetooth ───────────────────────────────────────────────────────────
        root.addView(sectionLabel("Bluetooth", dp16, dp8))

        val deviceNameEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = AppearanceStore.DEFAULT_DEVICE_NAME
            setSingleLine(true)
            maxEms = 32
            setText(AppearanceStore.getDeviceName(this@MainActivity))
        }
        root.addView(deviceNameEdit)

        root.addView(hintText(
            "Hosts will see this name when pairing. Changes apply the next time you prepare a new Bluetooth connection.",
            dp4, dp16
        ))

        root.addView(MaterialDivider(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp8; lp.bottomMargin = dp4
            layoutParams = lp
        })

        // ── Appearance ─────────────────────────────────────────────────────────
        root.addView(sectionLabel("Appearance", dp8, dp8))

        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf("System default", "Light", "Dark").forEachIndexed { i, label ->
            radioGroup.addView(RadioButton(this).apply {
                text = label; id = i; isChecked = (i == selectedAppearance)
            })
        }
        radioGroup.setOnCheckedChangeListener { _, id -> selectedAppearance = id }
        root.addView(radioGroup)

        val largeTextCheck = CheckBox(this).apply {
            text = "Large Text"
            isChecked = originalLargeText
        }
        root.addView(largeTextCheck)

        val highContrastCheck = CheckBox(this).apply {
            text = "High Contrast"
            isChecked = originalHighContrast
        }
        root.addView(highContrastCheck)

        // ── Divider ────────────────────────────────────────────────────────────
        root.addView(MaterialDivider(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp16; lp.bottomMargin = dp4
            layoutParams = lp
        })

        // ── Session Logging ────────────────────────────────────────────────────
        root.addView(sectionLabel("Session Logging", dp8, dp8))

        val loggingCheck = CheckBox(this).apply {
            text = "Enable local session logging"
            isChecked = LoggingStore.isEnabled(this@MainActivity)
        }
        root.addView(loggingCheck)

        root.addView(hintText(
            "On each connection a .config snapshot and a timestamped .log of all" +
            " HID events are written to:\n${LoggingStore.sessionDirDisplayPath(this)}",
            dp4, dp16
        ))

        // ── Divider ────────────────────────────────────────────────────────────
        root.addView(MaterialDivider(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp16; lp.bottomMargin = dp4
            layoutParams = lp
        })

        // ── Orientation Lock ───────────────────────────────────────────────────
        root.addView(sectionLabel("Orientation Lock", dp8, dp8))

        var selectedOrientation = OrientationStore.get(this)
        val orientationGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf("System default", "Portrait", "Landscape").forEachIndexed { i, label ->
            orientationGroup.addView(RadioButton(this).apply {
                text = label; id = i; isChecked = (i == selectedOrientation)
            })
        }
        orientationGroup.setOnCheckedChangeListener { _, id -> selectedOrientation = id }
        root.addView(orientationGroup)

        root.addView(hintText(
            "Locks screen rotation for Touch Mouse and Gamepad canvas views.",
            dp4, dp16
        ))

        // ── Build dialog ───────────────────────────────────────────────────────
        MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Apply") { _, _ ->
                AppearanceStore.setDeviceName(this, deviceNameEdit.text.toString())
                AppearanceStore.set(this, selectedAppearance)
                AppCompatDelegate.setDefaultNightMode(AppearanceStore.toNightMode(selectedAppearance))
                AppearanceStore.setLargeText(this, largeTextCheck.isChecked)
                AppearanceStore.setHighContrast(this, highContrastCheck.isChecked)
                val enabled = loggingCheck.isChecked
                LoggingStore.setEnabled(this, enabled)
                viewModel.setLoggingEnabled(enabled)
                OrientationStore.set(this, selectedOrientation)
                requestedOrientation = OrientationStore.toActivityOrientation(selectedOrientation)
                if (
                    largeTextCheck.isChecked != originalLargeText ||
                    highContrastCheck.isChecked != originalHighContrast
                ) {
                    recreate()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sectionLabel(text: String, topPad: Int, bottomPad: Int) = TextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        setPadding(0, topPad, 0, bottomPad)
    }

    private fun hintText(text: String, topPad: Int, bottomPad: Int) = TextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        setPadding(0, topPad, 0, bottomPad)
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
                Manifest.permission.BLUETOOTH_SCAN
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
        }
    }
}
