package com.albert.catalog.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class ProductRequest(
    @field:NotNull(message = "Gold ID is required")
    @field:Positive(message = "Gold ID must be positive")
    @field:JsonProperty("gold_id")
    val goldId: Long,

    @field:NotBlank(message = "Long name is required")
    @field:JsonProperty("long_name")
    val longName: String,

    @field:NotBlank(message = "Short name is required")
    @field:JsonProperty("short_name")
    val shortName: String,

    @field:NotBlank(message = "IOW unit type is required")
    @field:JsonProperty("iow_unit_type")
    val iowUnitType: String,

    @field:NotBlank(message = "Healthy category is required")
    @field:JsonProperty("healthy_category")
    val healthyCategory: String,
)
