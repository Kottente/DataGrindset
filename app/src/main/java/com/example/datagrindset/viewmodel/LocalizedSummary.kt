package com.example.datagrindset.viewmodel

import androidx.annotation.StringRes

data class LocalizedSummary(
    @StringRes val stringResId: Int,
    val formatArgs: List<Any> = emptyList()
)