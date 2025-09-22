package com.albert.catalog.dto

/**
 * Result of batch processing operation
 */
data class BatchResult(
    val saved: Int,
    val updated: Int,
    val errors: Int,
)
