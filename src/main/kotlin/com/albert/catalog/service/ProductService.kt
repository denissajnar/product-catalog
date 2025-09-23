package com.albert.catalog.service

import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.multipart.MultipartFile

interface ProductService {

    fun findAll(pageable: Pageable): List<ProductResponse>

    fun findById(id: Long): ProductResponse?

    fun update(id: Long, productRequest: ProductRequest): ProductResponse?

    fun delete(id: Long): Boolean

    fun getPagedProducts(pageable: Pageable): Page<ProductResponse>

    fun importProducts(file: MultipartFile)
}
