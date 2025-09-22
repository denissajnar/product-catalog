package com.albert.catalog.dto

data class ProductPageResponse(
    val content: List<ProductResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)
