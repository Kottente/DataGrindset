package com.example.datagrindset

enum class ThemeOption {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromString(name: String?): ThemeOption {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}