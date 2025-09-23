package com.albert.catalog.repository

import com.albert.catalog.entity.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : JpaRepository<Product, Long> {
    fun findAllBy(pageable: Pageable): Page<Product>
    fun findByGoldIdIn(goldIds: Collection<Long>): List<Product>
}
