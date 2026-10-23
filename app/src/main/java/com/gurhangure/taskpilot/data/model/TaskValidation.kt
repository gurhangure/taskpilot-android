package com.gurhangure.taskpilot.data.model

/** Matches .NET whitespace and UTF-16 length validation without trimming the supplied content. */
internal fun taskValidationErrors(title: String, description: String?): Map<String, List<String>> = buildMap {
    val blank = title.all { it in '\u0009'..'\u000D' || it == '\u0085' || Character.isSpaceChar(it) }
    if (blank || title.length > 200) {
        put("Title", listOf("Enter a nonblank title of at most 200 characters."))
    }
    if (description != null && description.length > 2000) {
        put("Description", listOf("Use at most 2000 characters for the description."))
    }
}
