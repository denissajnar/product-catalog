package com.albert.catalog.repository

import com.albert.catalog.entity.Product
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProductRepository : CoroutineCrudRepository<Product, UUID> {

    fun findAllBy(pageable: Pageable): Flow<Product>
}
