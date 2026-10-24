package com.xtrakick.app.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Locale

internal fun JsonObject.primitiveOrNull(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
}

internal fun JsonObject.intOrNull(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
}

internal fun JsonObject.firstPrimitiveOrNull(vararg keys: String): String? {
    keys.forEach { key ->
        primitiveOrNull(key)?.let { return it }
    }
    return null
}

internal fun JsonObject.firstLongOrNull(vararg keys: String): Long? {
    keys.forEach { key ->
        (this[key] as? JsonPrimitive)?.let { primitive ->
            primitive.longOrNull?.let { return it }
            primitive.contentOrNull?.toLongOrNull()?.let { return it }
        }
    }
    return null
}

internal fun JsonObject.firstBooleanOrNull(vararg keys: String): Boolean? {
    keys.forEach { key ->
        (this[key] as? JsonPrimitive)?.let { primitive ->
            primitive.contentOrNull?.toBooleanStrictOrNull()?.let { return it }
            primitive.intOrNull?.let { return it != 0 }
        }
    }
    return null
}

internal fun JsonObject.objOrNull(key: String): JsonObject? {
    return this[key] as? JsonObject
}

internal fun JsonObject.firstObjectOrNull(vararg keys: String): JsonObject? {
    keys.forEach { key ->
        objOrNull(key)?.let { return it }
    }
    return null
}

internal fun JsonObject.arrayOrNull(key: String): JsonArray? {
    return this[key] as? JsonArray
}

internal fun JsonElement?.asBooleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    return when (primitive.contentOrNull?.trim()?.lowercase(Locale.ROOT)) {
        "1", "true", "yes", "active", "subscribed" -> true
        "0", "false", "no", "inactive", "unsubscribed" -> false
        else -> null
    }
}

internal fun findInterestingKeyPaths(root: JsonElement?, fragments: Set<String>): List<String> {
    if (root == null) return emptyList()
    val matches = mutableListOf<String>()

    fun walk(element: JsonElement, path: List<String>) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) ->
                    val lower = key.lowercase(Locale.ROOT)
                    val nextPath = path + key
                    if (fragments.any { lower.contains(it) }) {
                        matches += nextPath.joinToString(".")
                    }
                    if (matches.size < 32) {
                        walk(value, nextPath)
                    }
                }
            }
            is JsonArray -> {
                element.forEachIndexed { index, child ->
                    if (matches.size < 32) {
                        walk(child, path + "[$index]")
                    }
                }
            }
            else -> Unit
        }
    }

    walk(root, emptyList())
    return matches.distinct()
}
