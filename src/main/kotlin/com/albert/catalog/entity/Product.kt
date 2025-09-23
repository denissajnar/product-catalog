package com.albert.catalog.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "products", schema = "catalog", indexes = [Index(name = "idx_products_gold_id", columnList = "gold_id")])
@EntityListeners(AuditingEntityListener::class)
class Product(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotNull
    var uuid: UUID,

    @field:NotNull
    @Column(name = "gold_id")
    var goldId: Long,

    @field:NotBlank
    @Column(name = "long_name")
    var longName: String,

    @field:NotBlank
    @Column(name = "short_name")
    var shortName: String,

    @field:NotBlank
    @Column(name = "iow_unit_type")
    var iowUnitType: String,

    @field:NotBlank
    @Column(name = "healthy_category")
    var healthyCategory: String,

    @CreatedDate
    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Version
    var version: Long = 0L,
)
