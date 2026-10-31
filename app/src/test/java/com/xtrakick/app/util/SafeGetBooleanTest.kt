package com.xtrakick.app.util

import android.content.Context
import androidx.core.content.edit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class SafeGetBooleanTest {

    @Test
    fun returnsNormalBooleanWhenStoredAsBoolean() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs_bool", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("bool_key", true) }

        assertTrue(prefs.safeGetBoolean("bool_key", false))
    }

    @Test
    fun parsesBooleanStringGracefullyWhenStoredAsString() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs_string", Context.MODE_PRIVATE)
        prefs.edit { putString("str_key_true", "true") }
        prefs.edit { putString("str_key_false", "false") }

        assertTrue(prefs.safeGetBoolean("str_key_true", false))
        assertFalse(prefs.safeGetBoolean("str_key_false", true))
    }

    @Test
    fun returnsDefaultWhenKeyMissingOrNonBooleanString() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs_missing", Context.MODE_PRIVATE)
        prefs.edit { putString("str_key_invalid", "10000") }

        assertTrue(prefs.safeGetBoolean("missing_key", true))
        assertFalse(prefs.safeGetBoolean("missing_key", false))
        assertTrue(prefs.safeGetBoolean("str_key_invalid", true))
        assertFalse(prefs.safeGetBoolean("str_key_invalid", false))
    }
}
