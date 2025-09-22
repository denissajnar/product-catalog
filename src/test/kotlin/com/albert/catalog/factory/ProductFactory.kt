package com.albert.catalog.factory

import com.albert.catalog.entity.Product
import java.util.*
import kotlin.random.Random

fun createProduct(
    uuid: UUID = UUID.randomUUID(),
    goldId: Long = Random.nextLong(),
    longName: String = "Test Product Long Name",
    shortName: String = "Test Product",
    iowUnitType: String = "PIECE",
    healthyCategory: String = "GREEN",
) = Product(
    uuid = uuid,
    goldId = goldId,
    longName = longName,
    shortName = shortName,
    iowUnitType = iowUnitType,
    healthyCategory = healthyCategory,
)
