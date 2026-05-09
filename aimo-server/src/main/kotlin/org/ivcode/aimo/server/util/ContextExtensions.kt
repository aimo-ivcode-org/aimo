package org.ivcode.aimo.server.util

import org.ivcode.aimo.server.model.RequestMetadata

internal const val PROPERTY_NAME_REQUEST_METADATA = "requestMetadata"

fun Map<String, Any>.getRequestMetadata(): RequestMetadata? {
    return this[PROPERTY_NAME_REQUEST_METADATA] as? RequestMetadata
}