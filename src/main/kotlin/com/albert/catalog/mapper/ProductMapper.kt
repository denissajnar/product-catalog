package com.albert.catalog.mapper

import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product

fun Product.toResponse(): ProductResponse =
    ProductResponse(
        goldId = this.goldId,
        longName = this.longName,
        shortName = this.shortName,
        iowUnitType = this.iowUnitType,
        healthyCategory = this.healthyCategory,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun ProductRequest.toEntity(existingProduct: Product): Product {
    existingProduct.goldId = this.goldId
    existingProduct.longName = this.longName
    existingProduct.shortName = this.shortName
    existingProduct.iowUnitType = this.iowUnitType
    existingProduct.healthyCategory = this.healthyCategory

    return existingProduct
}
