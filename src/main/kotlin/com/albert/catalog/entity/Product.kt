package com.albert.catalog.entity

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.*

@Table(name = "products", schema = "catalog")
data class Product(
    @Id
    val uuid: UUID? = null,

    @field:NotNull
    @Column("gold_id")
    val goldId: Long,

    @field:NotBlank
    @Column("long_name")
    val longName: String,

    @field:NotBlank
    @Column("short_name")
    val shortName: String,

    @field:NotBlank
    @Column("iow_unit_type")
    val iowUnitType: String,

    @field:NotBlank
    @Column("healthy_category")
    val healthyCategory: String,

    @Column("created_at")
    val createdAt: LocalDateTime? = null,

    @Column("updated_at")
    val updatedAt: LocalDateTime? = null,
)
