package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ui.theme.* // Import all your color schemes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.content.edit

data class AppThemeForVM(
    val name: String,
    val description: String, // Keep for display if needed
    val lightColors: ColorScheme,
    val darkColors: ColorScheme
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
    private val themeKey = "selected_theme_name"

    val availableThemes: List<AppThemeForVM> = listOf(
        AppThemeForVM("Classic", "", ClassicLightColors, ClassicDarkColors),
        AppThemeForVM("Sky", "", SkyLightColors, SkyDarkColors),
        AppThemeForVM("Forest Lagoon", "", ForestLagoonLightColors, ForestLagoonDarkColors),
        AppThemeForVM("Noir & Blanc", "", NoirBlancLightColors, NoirBlancDarkColors),
        AppThemeForVM("Strawberry Milk", "", StrawberryMilkLightColors, StrawberryMilkDarkColors),
        AppThemeForVM("Strawberry & Kiwi", "", StrawberryKiwiLightColors, StrawberryKiwiDarkColors),
        AppThemeForVM("Solarized", "", SolarizedLightColors, SolarizedDarkColors),
        AppThemeForVM("Nordic Night", "", NordicNightLightColors, NordicNightDarkColors)

    )

    private val _currentThemePair = MutableStateFlow(loadThemePreference())
    val currentThemePair: StateFlow<Pair<ColorScheme, ColorScheme>> = _currentThemePair.asStateFlow()

    private val _selectedThemeName = MutableStateFlow(getSelectedThemeName())
    val selectedThemeName: StateFlow<String> = _selectedThemeName.asStateFlow()


    private fun loadThemePreference(): Pair<ColorScheme, ColorScheme> {
        val themeName = sharedPreferences.getString(themeKey, availableThemes.first().name) ?: availableThemes.first().name
        val theme = availableThemes.find { it.name == themeName } ?: availableThemes.first()
        return Pair(theme.lightColors, theme.darkColors)
    }

    private fun getSelectedThemeName(): String {
        return sharedPreferences.getString(themeKey, availableThemes.first().name) ?: availableThemes.first().name
    }

    fun selectTheme(themeName: String) {
        viewModelScope.launch {
            val selected = availableThemes.find { it.name == themeName }
            selected?.let {
                _currentThemePair.value = Pair(it.lightColors, it.darkColors)
                _selectedThemeName.value = it.name
                sharedPreferences.edit { putString(themeKey, themeName) }
            }
        }
    }
}