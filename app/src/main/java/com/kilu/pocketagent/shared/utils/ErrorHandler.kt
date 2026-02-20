package com.kilu.pocketagent.shared.utils

import com.kilu.pocketagent.shared.models.ErrorEnvelope
import kotlinx.serialization.json.Json
import okhttp3.Response

object ErrorHandler {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Extracts a user-friendly error message from an OkHttp Response.
     * Maps known HTTP status codes and tries to parse the 'ErrorEnvelope' from the backend.
     */
    fun parseError(response: Response): String {
        val code = response.code
        val bodyString = response.body?.string()

        val parsedError = try {
            if (!bodyString.isNullOrEmpty()) {
                val envelope = json.decodeFromString<ErrorEnvelope>(bodyString)
                envelope.message ?: envelope.error
            } else {
                null
            }
        } catch (e: Exception) {
            "Failed to parse server error format."
        }

        return when (code) {
            400 -> "Invalid Request: ${parsedError ?: "Check your inputs."}"
            401 -> "Unauthorized: ${parsedError ?: "Session expired or invalid."}"
            403 -> "Forbidden: ${parsedError ?: "You do not have permission for this action."}"
            404 -> "Not Found: ${parsedError ?: "The requested resource does not exist."}"
            409 -> "Conflict: ${parsedError ?: "The resource state has changed. Please refresh."}"
            429 -> "Rate Limited: ${parsedError ?: "Too many requests. Please slow down and try again later."}"
            in 500..599 -> "Server Error ($code): ${parsedError ?: "Our systems are experiencing issues."}"
            else -> "Error $code: ${parsedError ?: "An unknown error occurred."}"
        }
    }
}
