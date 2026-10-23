package com.gurhangure.taskpilot.data.model

/** Represents an operation outcome without exposing transport exceptions to presentation code. */
sealed interface ApiResult<out T> {
    /** Contains a server-confirmed value, including Unit for a successful deletion. */
    data class Success<T>(val value: T) : ApiResult<T>

    /** Contains a recoverable failure suitable for presentation-layer decisions. */
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Distinguishes actionable failure types without retaining response bodies or exception details. */
sealed interface ApiError {
    /** No usable endpoint has been selected. */
    data object NotConfigured : ApiError

    /** The supplied URL failed validation; the current client remains unchanged. */
    data object InvalidUrl : ApiError

    /** The saved endpoint could not be read or updated; existing settings are retained. */
    data object Storage : ApiError

    /** The server could not be reached or the transport failed. */
    data object Connection : ApiError

    /** The configured connection, read, write, or whole-call deadline elapsed. */
    data object Timeout : ApiError

    /** The requested resource does not exist; shared by get, update, and delete. */
    data object NotFound : ApiError

    /** ValidationProblemDetails field messages, or local content-validation messages. */
    data class Validation(val fields: Map<String, List<String>>) : ApiError

    /** An unsuccessful HTTP status outside validation and missing-resource cases. */
    data class Http(val statusCode: Int) : ApiError

    /** A successful response was empty, malformed, or did not match the task contract. */
    data object InvalidResponse : ApiError
}
