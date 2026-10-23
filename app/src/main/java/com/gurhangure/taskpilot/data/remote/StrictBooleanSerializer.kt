package com.gurhangure.taskpilot.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull

/** Requires a JSON Boolean token instead of accepting kotlinx.serialization's quoted coercion. */
internal object StrictBooleanSerializer : JsonTransformingSerializer<Boolean>(Boolean.serializer()) {
    /** Rejects strings, nulls, and other JSON types before the default Boolean decoder runs. */
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString || primitive.booleanOrNull == null) {
            throw SerializationException("Expected a JSON Boolean token.")
        }
        return element
    }
}
