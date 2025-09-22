package com.albert.catalog.dto

import java.time.LocalDateTime
import java.util.*

data class ProductResponse(
    val uuid: UUID,
    val goldId: Long,
    val longName: String,
    val shortName: String,
    val iowUnitType: String,
    val healthyCategory: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
