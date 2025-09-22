package com.albert.catalog.service

import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.http.codec.multipart.FilePart

interface ProductService {

    fun findAll(pageable: Pageable): Flow<ProductResponse>

    suspend fun findById(id: Long): ProductResponse?

    suspend fun update(id: Long, productRequest: ProductRequest): ProductResponse?

    suspend fun delete(id: Long): Boolean

    suspend fun getPagedProducts(pageable: Pageable): ProductPageResponse

    suspend fun importProducts(file: FilePart)
}
