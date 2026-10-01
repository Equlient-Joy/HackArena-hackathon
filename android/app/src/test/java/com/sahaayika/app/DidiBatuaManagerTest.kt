package com.sahaayika.app

import android.content.SharedPreferences
import com.sahaayika.app.data.profile.DidiBatuaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DidiBatuaManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var batuaManager: DidiBatuaManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        batuaManager = DidiBatuaManager(fakePrefs)
    }

    @Test
    fun testInitialState_IsEmpty() {
        assertFalse(batuaManager.hasProfile())
        assertNull(batuaManager.getName())
        assertNull(batuaManager.getAadhaar())
        assertNull(batuaManager.getSamagraId())
        assertNull(batuaManager.getMobile())
    }

    @Test
    fun testSaveAndRetrieveProfile() {
        batuaManager.saveProfile(
            name = "कमला बाई",
            aadhaar = "1234 5678 9012",
            samagraId = "987654321",
            mobile = "9876543210"
        )

        assertTrue(batuaManager.hasProfile())
        assertEquals("कमला बाई", batuaManager.getName())
        // Spaces should be stripped from Aadhaar
        assertEquals("123456789012", batuaManager.getAadhaar())
        assertEquals("987654321", batuaManager.getSamagraId())
        assertEquals("9876543210", batuaManager.getMobile())

        val profile = batuaManager.getProfile()
        assertTrue(profile.isComplete)
        assertEquals("कमला बाई", profile.name)
        assertEquals("123456789012", profile.aadhaar)
    }

    @Test
    fun testPartialProfile() {
        batuaManager.saveProfile(
            name = "सुनीता",
            aadhaar = null,
            samagraId = "11223344",
            mobile = null
        )

        assertTrue(batuaManager.hasProfile())
        assertEquals("सुनीता", batuaManager.getName())
        assertNull(batuaManager.getAadhaar())
        assertEquals("11223344", batuaManager.getSamagraId())
        assertNull(batuaManager.getMobile())

        val profile = batuaManager.getProfile()
        assertFalse(profile.isComplete)
    }

    @Test
    fun testClearProfile() {
        batuaManager.saveProfile(
            name = "आरती",
            aadhaar = "111122223333",
            samagraId = "444555",
            mobile = "9988776655"
        )
        assertTrue(batuaManager.hasProfile())

        batuaManager.clearProfile()
        assertFalse(batuaManager.hasProfile())
        assertNull(batuaManager.getName())
        assertNull(batuaManager.getAadhaar())
        assertNull(batuaManager.getSamagraId())
        assertNull(batuaManager.getMobile())
    }
}

/**
 * In-memory FakeSharedPreferences implementation for JVM unit tests.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? {
        return (data[key] as? String) ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (data[key] as? MutableSet<String>) ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val sharedData: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pendingChanges = mutableMapOf<String, Any?>()
        private val pendingRemovals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                if (value != null) pendingChanges[key] = value else pendingRemovals.add(key)
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pendingChanges[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pendingChanges[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pendingChanges[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pendingChanges[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pendingChanges[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pendingRemovals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                sharedData.clear()
            }
            pendingRemovals.forEach { sharedData.remove(it) }
            pendingChanges.forEach { (k, v) ->
                if (v == null) sharedData.remove(k) else sharedData[k] = v
            }
            pendingChanges.clear()
            pendingRemovals.clear()
            clearRequested = false
        }
    }
}
