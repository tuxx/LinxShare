package com.kolktech.linxshare

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Example instrumented test, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.kolktech.linxshare", appContext.packageName)
    }
}


