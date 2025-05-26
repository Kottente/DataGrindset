package com.example.datagrindset.ui

// Moved SortOption to its own file to be accessible by ViewModel and Screen

enum class SortOption(val displayName: String) {
    BY_NAME_ASC("Name (A-Z)"),
    BY_NAME_DESC("Name (Z-A)"),
    BY_DATE_ASC("Date (Oldest)"),
    BY_DATE_DESC("Date (Newest)"),
    BY_SIZE_ASC("Size (Smallest)"),
    BY_SIZE_DESC("Size (Largest)")
}
enum class ProcessingStatus {
    NONE,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILURE,
    UNSUPPORTED,
    ERROR,
    FAILED
}