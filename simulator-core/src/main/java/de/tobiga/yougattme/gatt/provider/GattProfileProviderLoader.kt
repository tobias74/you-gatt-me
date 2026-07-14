package de.tobiga.yougattme.gatt.provider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

private const val TAG = "GattProfileProviderLoader"

object GattProfileProviderLoader {
    const val METADATA_PREFIX = "de.tobiga.yougattme.gatt.provider."

    fun loadFromManifest(context: Context): List<GattProfileProvider> {
        val metadata = try {
            val packageManager = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            }
            appInfo.metaData
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to read provider metadata", exception)
            null
        } ?: return emptyList()

        return metadata.keySet()
            .filter { key -> key.startsWith(METADATA_PREFIX) }
            .mapNotNull { key -> metadata.getString(key) }
            .distinct()
            .mapNotNull { className -> instantiateProvider(className) }
    }

    private fun instantiateProvider(className: String): GattProfileProvider? {
        return try {
            val providerClass = Class.forName(className)
            providerClass.getDeclaredConstructor().newInstance() as? GattProfileProvider
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to instantiate GATT profile provider $className", exception)
            null
        }
    }
}
