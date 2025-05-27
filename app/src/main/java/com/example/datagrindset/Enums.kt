package com.example.datagrindset

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

// New Enum for View Type
enum class ViewType {
    LIST,
    GRID
}