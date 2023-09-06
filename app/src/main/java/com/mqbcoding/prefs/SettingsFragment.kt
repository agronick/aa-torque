package com.mqbcoding.prefs

import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.mqbcoding.datastore.Screen
import com.mqbcoding.stats.CarStatsLogger
import com.mqbcoding.stats.R
import com.mqbcoding.stats.TemperaturePreference
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Collections

class SettingsFragment : PreferenceFragmentCompat() {
    lateinit var numScreensPref: EditTextPreference
    lateinit var dashboardsCat: PreferenceCategory

    @Throws(IOException::class)
    private fun findLogs(): List<File> {
        val logDir = CarStatsLogger.getLogsDir()
        val files: MutableList<File> = ArrayList()
        for (f in logDir.listFiles()) {
            if (f.name.endsWith(".log.gz")) {
                files.add(f)
            }
        }
        Collections.sort(files)
        return files
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dashboardsCat = findPreference("dashboardsCat")!!
        numScreensPref = findPreference("dashboardCount")!!
        numScreensPref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        numScreensPref.setOnPreferenceChangeListener {
                preference, newValue ->
            val intVal = (newValue as String).toInt()
            if (intVal in 1..10) {
                lifecycleScope.launch {
                    requireContext().dataStore.updateData { currentSettings ->
                        var bldr = currentSettings.toBuilder()
                        if (bldr.screensCount > intVal) {
                            val keeping = bldr.screensList.subList(0, intVal)
                            bldr = bldr.clearScreens().addAllScreens(keeping)
                        } else if (currentSettings.screensCount < intVal) {
                            val newItms = Collections.nCopies(
                                intVal - currentSettings.screensCount,
                                UserPreferenceSerializer.defaultScreen.build()
                            )
                            bldr = bldr.addAllScreens(newItms.toMutableList())
                            Log.d(TAG, "${newItms.size} added, ${intVal} specified")
                        }
                        return@updateData bldr.build()
                    }
                }
                return@setOnPreferenceChangeListener true
            }
            return@setOnPreferenceChangeListener false
        }

        lifecycleScope.launch {
            requireContext().dataStore.data.distinctUntilChangedBy{
                it.screensCount
            }.collect { userPreference ->
                dashboardsCat.removeAll()
                userPreference.screensList.forEachIndexed {
                        i, screen ->
                    dashboardsCat.addPreference(Preference(requireContext()).also {
                        it.title = requireContext().getString(
                            R.string.pref_dataelementsettings_1
                        ).replace("1", (i + 1).toString())
                        it.key = "dashboard_$i"
                        it.fragment = "com.mqbcoding.prefs.SettingsDashboard"
                        it.summary = screen.title
                    })
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
    }

    companion object {
        private const val TAG = "PreferenceFragment"
    }
}