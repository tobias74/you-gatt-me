package de.tobiga.yougattme

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import de.tobiga.yougattme.databinding.ActivityMainBinding
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val TERMS_AND_CONDITIONS_READ_VERSION = "TERMS_AND_CONDITIONS_READ_VERSION"
const val CURRENT_VERSION_TERMS_AND_CONDITIONS = 4

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val viewModel: ApplicationViewModel by viewModels()

    // New permission launcher using ActivityResultContracts
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                // Initialize ApplicationState only after permissions are granted.
                initializeApplicationState()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up view binding and layout.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        prefs = PreferenceManager.getDefaultSharedPreferences(application)

        // Set up navigation.
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_gatt_services), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Check for required permissions BEFORE initializing ApplicationState.
        if (allPermissionsGranted()) {
            initializeApplicationState()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    // Initialize ApplicationState after permissions are granted.
    private fun initializeApplicationState() {
        val applicationState = ApplicationState.getInstance(this)
        applicationState.mainViewModel = viewModel
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // Check if all required permissions are granted.
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        // Include all Bluetooth permissions needed for Android 12+.
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).toTypedArray()
    }

    override fun onResume() {
        super.onResume()
        if (!hasReadTermsAndConditions()) {
            showTermsAndConditions()
        }
    }

    private fun showTermsAndConditions() {
        val builder = AlertDialog.Builder(this)
        val dialogLayout = layoutInflater.inflate(R.layout.toc_layout, null)

        var myLocale = Locale.getDefault().toString()
        if (myLocale != "de_DE" && myLocale != "en_EN") {
            myLocale = "en_EN"
        }

        val webView: WebView = dialogLayout.findViewById(R.id.tocWebView)
        webView.loadUrl("file:///android_asset/terms$myLocale.html")

        with(builder) {
            setCancelable(false)
            setPositiveButton(R.string.ok) { dialog, which ->
                val edit = prefs.edit()
                edit.putInt(TERMS_AND_CONDITIONS_READ_VERSION, CURRENT_VERSION_TERMS_AND_CONDITIONS)
                edit.commit()
            }
            setView(dialogLayout)
            show()
        }
    }

    private fun hasReadTermsAndConditions(): Boolean {
        return prefs.getInt(TERMS_AND_CONDITIONS_READ_VERSION, 0) >= CURRENT_VERSION_TERMS_AND_CONDITIONS
    }
}
