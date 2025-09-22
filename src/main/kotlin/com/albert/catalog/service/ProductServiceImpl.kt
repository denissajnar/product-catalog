package com.albert.catalog.service

import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import com.albert.catalog.mapper.toEntity
import com.albert.catalog.mapper.toResponse
import com.albert.catalog.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.data.domain.Pageable
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
) : ProductService {

    override fun findAll(pageable: Pageable): Flow<ProductResponse> =
        productRepository.findAllBy(pageable)
            .map { it.toResponse() }

    override suspend fun findById(id: Long): ProductResponse? =
        productRepository.findById(id)?.toResponse()

    @Transactional
    override suspend fun update(id: Long, productRequest: ProductRequest): ProductResponse? =
        productRepository.findById(id)
            ?.let { productRequest.toEntity(it) }
            ?.let { productRepository.save(it) }
            ?.toResponse()

    @Transactional
    override suspend fun delete(id: Long) =
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

    @Transactional
    override suspend fun importProducts(file: FilePart) {
        val dataBuffer = DataBufferUtils.join(file.content()).awaitSingle()
        val inputStream = dataBuffer.asInputStream()

        val products = mutableListOf<Product>()
        val batchSize = 1000

        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            val header = reader.readLine()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { csvLine ->
                    try {
                        val product = parseCsvLine(csvLine)
                        products.add(product)

                        if (products.size >= batchSize) {
                            saveProductsBatch(products)
                            products.clear()
                        }
                    } catch (e: Exception) {
                        println("Error processing line: $csvLine - ${e.message}")
                    }
                }
            }

            if (products.isNotEmpty()) {
                saveProductsBatch(products)
            }
        }
    }

    private suspend fun saveProductsBatch(products: List<Product>) =
        productRepository.saveAll(products).toList()

    private fun parseCsvLine(line: String): Product {
        // Parse CSV line handling quoted values
        val values = parseCsvValues(line)

        if (values.size != 6) {
            throw IllegalArgumentException("Expected 6 columns, got ${values.size}")
        }

        return Product(
            uuid = UUID.fromString(values[0]),
            goldId = values[1].toLong(),
            longName = values[2],
            shortName = values[3],
            iowUnitType = values[4],
            healthyCategory = values[5],
        )
    }

    private fun parseCsvValues(line: String): List<String> {
        val values = mutableListOf<String>()
        val currentValue = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when {
                char == '"' && !inQuotes -> {
                    // Starting a quoted field
                    inQuotes = true
                }

                char == '"' && inQuotes -> {
                    // Check if this is an escaped quote or end of quoted field
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote - add one quote to the value and skip the next quote
                        currentValue.append('"')
                        i++ // Skip the next quote
                    } else {
                        // End of quoted field
                        inQuotes = false
                    }
                }

                char == ',' && !inQuotes -> {
                    values.add(currentValue.toString().trim())
                    currentValue.clear()
                }

                else -> {
                    currentValue.append(char)
                }
            }
            i++
        }

        // Add the last value
        values.add(currentValue.toString().trim())

        return values
    }
}
