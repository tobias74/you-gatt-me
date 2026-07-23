package de.tobiga.yougattme.bridge

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BleBridgeServiceTest {
    @get:Rule val bluetoothPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )
    @get:Rule val serviceRule = ServiceTestRule()

    @Test fun serviceStartsBindsAndPublishesRuntimeState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val binder = serviceRule.bindService(Intent(context, BleBridgeService::class.java))
        assertTrue(binder is BleBridgeService.LocalBinder)
        assertEquals(true, BridgeStateStore.shared.serviceRunning.value)
    }

    @Test fun manifestDeclaresConnectedDeviceForegroundServiceAndPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)

        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, BleBridgeService::class.java),
            PackageManager.ComponentInfoFlags.of(0)
        )
        assertTrue(serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0)
    }
}
