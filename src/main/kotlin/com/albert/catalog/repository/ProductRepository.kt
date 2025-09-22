package com.albert.catalog.repository

import com.albert.catalog.entity.Product
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : CoroutineCrudRepository<Product, Long> {
    fun findAllBy(pageable: Pageable): Flow<Product>
    fun findByGoldIdIn(goldIds: Collection<Long>): Flow<Product>
}
