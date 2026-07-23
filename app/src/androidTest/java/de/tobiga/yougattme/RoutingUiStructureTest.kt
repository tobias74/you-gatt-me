package de.tobiga.yougattme

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutingUiStructureTest {
    @Test
    fun inputPanelContainsOnlyInputsAndDiagnosticsIsSeparate() {
        val applicationContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = ContextThemeWrapper(applicationContext, R.style.Theme_YouGattMe_NoActionBar)
        lateinit var root: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        }
        val texts = root.allText().toSet()

        assertNotNull(root.findViewById<View>(R.id.headerInputDevices))
        assertNotNull(root.findViewById<View>(R.id.headerBridgeDiagnostics))
        assertEquals(View.GONE, root.findViewById<View>(R.id.contentBridgeDiagnostics).visibility)
        assertFalse("OUTPUT ROUTES" in texts)
        assertFalse("BRIDGE LOG" in texts)
        assertFalse(texts.any { it.contains("Input devices & routing") })
    }

    private fun View.allText(): List<String> = buildList {
        if (this@allText is TextView) add(this@allText.text.toString())
        if (this@allText is ViewGroup) {
            for (index in 0 until this@allText.childCount) {
                addAll(this@allText.getChildAt(index).allText())
            }
        }
    }
}
