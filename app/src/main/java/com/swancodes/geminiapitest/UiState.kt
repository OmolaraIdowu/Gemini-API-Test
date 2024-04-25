package com.swancodes.geminiapitest

import android.net.Uri

/**
 * A sealed hierarchy describing the state of the text generation.
 */
sealed interface UiState {

    /**
     * Empty state when the screen is first shown
     */
    object Initial : UiState

    /**
     * Image has been captured
     */
    data class Captured(val capturedImage: Uri) : UiState

    /**
     * Still loading
     */
    object Loading : UiState
    /**
     * Text has been generated
     */
    data class Success(val outputText: String) : UiState

    /**
     * There was an error generating text
     */
    data class Error(val errorMessage: String) : UiState
}