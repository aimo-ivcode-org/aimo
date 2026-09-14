package org.ivcode.aimo.server.model

import jakarta.servlet.http.HttpServletRequest
import java.util.Collections
import java.util.Locale

data class RequestMetadata(
    val path: String,
    val headers: Map<String, List<String>>,
) {
    companion object {
        fun from(request: HttpServletRequest): RequestMetadata {
            val headerSnapshot = linkedMapOf<String, MutableList<String>>()

            Collections.list(request.headerNames).forEach { headerName ->
                val normalizedHeaderName = headerName.lowercase(Locale.ROOT)
                headerSnapshot
                    .getOrPut(normalizedHeaderName) { mutableListOf() }
                    .addAll(Collections.list(request.getHeaders(headerName)))
            }

            return RequestMetadata(
                path = request.requestURI,
                headers = headerSnapshot.mapValues { (_, values) -> values.toList() },
            )
        }
    }
}
