package com.albert.catalog.dto

import java.time.LocalDateTime

data class ProductResponse(
    val goldId: Long,
    val longName: String,
    val shortName: String,
    val iowUnitType: String,
    val healthyCategory: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
