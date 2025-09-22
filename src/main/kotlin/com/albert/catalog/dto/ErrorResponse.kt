package com.albert.catalog.dto

import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val errors: Map<String, String>? = null,
)
