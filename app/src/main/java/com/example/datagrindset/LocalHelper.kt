package com.example.datagrindset

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.util.Locale

object LocaleHelper {

    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    private const val PREFS_NAME = "DataGrindsetPrefs"
    private const val SELECTED_THEME = "Theme.Helper.Selected.Theme" // New Key
    private const val TAG = "LocaleHelper"


    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // This method is called from Activity's attachBaseContext
    fun onAttach(context: Context): Context {
        val persistedLang = getPersistedLanguage(context, Locale.getDefault().language)
        Log.d(TAG, "onAttach: Attaching with persisted language: $persistedLang")
        return updateResources(context, persistedLang)
    }

    // This is called when the user makes a new selection in settings
    fun persistUserChoice(context: Context, language: String?) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()
        Log.d(TAG, "Persisted language choice: $language")
    }

    // This is used by SettingsScreen to display the current selection
    fun getLanguage(context: Context): String {
        val lang = getPersistedLanguage(context, Locale.getDefault().language)
        // Log.d(TAG, "getLanguage: Current language for UI display: $lang") // Can be noisy
        return lang
    }

    private fun getPersistedLanguage(context: Context, defaultLanguage: String): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale) // Set default for the JVM

        val currentRes = context.resources
        val config = Configuration(currentRes.configuration) // Create a new Configuration from current

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            // For API 24+, you might also consider setting locales list
            // val localeList = android.os.LocaleList(locale)
            // config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale) // Set layout direction based on locale
            Log.d(TAG, "updateResources: Applying new configuration for language: $language")
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            currentRes.updateConfiguration(config, currentRes.displayMetrics)
            Log.d(TAG, "updateResources (legacy): Updated resources for language: $language")
            return context
        }
    }
    // --- Theme ---
    fun persistThemeOption(context: Context, themeOption: ThemeOption) {
        getPreferences(context).edit().putString(SELECTED_THEME, themeOption.name).apply()
    }

    fun getThemeOption(context: Context): ThemeOption {
        val themeName = getPreferences(context).getString(SELECTED_THEME, ThemeOption.SYSTEM.name)
        return ThemeOption.fromString(themeName)
    }
}