package com.albert.catalog.service

import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import com.albert.catalog.mapper.toEntity
import com.albert.catalog.mapper.toResponse
import com.albert.catalog.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.data.domain.Pageable
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.*

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
) : ProductService {

    override fun findAll(pageable: Pageable): Flow<ProductResponse> =
        productRepository.findAllBy(pageable)
            .map { it.toResponse() }

    override suspend fun findById(id: UUID): ProductResponse? =
        productRepository.findById(id)?.toResponse()

    @Transactional
    override suspend fun update(id: UUID, productRequest: ProductRequest): ProductResponse? {
        val existingProduct = productRepository.findById(id) ?: return null

        val updatedProduct = productRequest.toEntity(existingProduct).copy(
            updatedAt = LocalDateTime.now(),
        )

        val savedProduct = productRepository.save(updatedProduct)

        return savedProduct.toResponse()
    }

    @Transactional
    override suspend fun delete(id: UUID) =
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id)
            true
        } else {
            false
        }

    @Transactional(readOnly = true)
    override suspend fun getPagedProducts(pageable: Pageable): ProductPageResponse {
        val products = findAll(pageable).toList()
        val totalElements = productRepository.count()
        val totalPages = ((totalElements + pageable.pageSize - 1) / pageable.pageSize).toInt()

        return ProductPageResponse(
            content = products,
            page = pageable.pageNumber,
            size = pageable.pageSize,
            totalElements = totalElements,
            totalPages = totalPages,
            first = pageable.pageNumber == 0,
            last = pageable.pageNumber >= totalPages - 1,
        )
    }

    override suspend fun importProducts(file: FilePart) {
        val content = DataBufferUtils.join(file.content())
            .map { dataBuffer ->
                val content = dataBuffer.toString(StandardCharsets.UTF_8)
                DataBufferUtils.release(dataBuffer)
                content
            }
            .awaitSingle()

        val lines = content.lines()

        lines.drop(1)
            .filter { it.isNotBlank() }
            .chunked(1000)
            .forEach { batch ->
                val products = batch.mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 6) {
                        Product(
                            uuid = parts[0].trim().takeIf { it.isNotBlank() }?.let { UUID.fromString(it) },
                            goldId = parts[1].trim().toLongOrNull() ?: 0L,
                            longName = parts[2].trim(),
                            shortName = parts[3].trim(),
                            iowUnitType = parts[4].trim(),
                            healthyCategory = parts[5].trim(),
                        )
                    } else null
                }

                productRepository.saveAll(products).collect()
            }
    }
}
