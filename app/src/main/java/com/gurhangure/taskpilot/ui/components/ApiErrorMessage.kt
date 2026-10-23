package com.gurhangure.taskpilot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gurhangure.taskpilot.R
import com.gurhangure.taskpilot.data.model.ApiError

/** Converts typed repository errors into concise connection or API recovery instructions. */
@Composable
fun apiErrorMessage(error: ApiError): String = when (error) {
    ApiError.Storage -> stringResource(R.string.tasks_error_storage)
    ApiError.NotConfigured -> stringResource(R.string.tasks_error_not_configured)
    ApiError.InvalidUrl -> stringResource(R.string.tasks_error_invalid_url)
    ApiError.Connection -> stringResource(R.string.tasks_error_connection)
    ApiError.Timeout -> stringResource(R.string.tasks_error_timeout)
    ApiError.NotFound -> stringResource(R.string.tasks_error_not_found)
    is ApiError.Validation -> stringResource(R.string.tasks_error_validation)
    is ApiError.Http -> stringResource(R.string.tasks_error_http, error.statusCode)
    ApiError.InvalidResponse -> stringResource(R.string.tasks_error_response)
}
