package de.tobiga.yougattme.bridge

import android.content.Context
import android.content.pm.PackageManager

/** Loads optional public/private input adapters declared as application manifest metadata. */
object InputAdapterProviderLoader {
    private const val PREFIX = "de.tobiga.yougattme.INPUT_ADAPTER_PROVIDER."

    fun loadFromManifest(context: Context): List<InputAdapterProvider> {
        val info = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
        val metadata = info.metaData ?: return emptyList()
        return metadata.keySet().asSequence()
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { key ->
                val className = metadata.getString(key) ?: return@mapNotNull null
                runCatching {
                    Class.forName(className).getDeclaredConstructor().newInstance() as InputAdapterProvider
                }.getOrNull()
            }
            .toList()
    }
}
