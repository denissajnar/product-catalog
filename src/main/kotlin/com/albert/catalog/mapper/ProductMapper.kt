package com.albert.catalog.mapper

import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import java.time.LocalDateTime
import java.util.*

fun Product.toResponse(): ProductResponse = ProductResponse(
    uuid = this.uuid ?: UUID.randomUUID(),
    goldId = this.goldId,
    longName = this.longName,
    shortName = this.shortName,
    iowUnitType = this.iowUnitType,
    healthyCategory = this.healthyCategory,
    createdAt = this.createdAt ?: LocalDateTime.now(),
    updatedAt = this.updatedAt ?: LocalDateTime.now(),
)

fun ProductRequest.toEntity(existingProduct: Product): Product = existingProduct.copy(
    goldId = this.goldId,
    longName = this.longName,
    shortName = this.shortName,
    iowUnitType = this.iowUnitType,
    healthyCategory = this.healthyCategory,
    updatedAt = LocalDateTime.now(),
)
