package com.albert.catalog.service

import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import com.albert.catalog.mapper.toEntity
import com.albert.catalog.mapper.toResponse
import com.albert.catalog.repository.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.data.domain.Pageable
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
) : ProductService {

    companion object {
        private val log = KotlinLogging.logger {}
        const val DEFAULT_BATCH_SIZE = 1000
        const val FIRST_PAGE_INDEX = 0
        const val PAGE_CALCULATION_OFFSET = 1
    }

    override fun findAll(pageable: Pageable): Flow<ProductResponse> =
        productRepository.findAllBy(pageable)
            .map { it.toResponse() }

    override suspend fun findById(id: Long): ProductResponse? {
        log.debug { "Finding product by id: $id" }
        return productRepository.findById(id)?.toResponse()
    }

    @Transactional
    override suspend fun update(id: Long, productRequest: ProductRequest): ProductResponse? {
        log.debug { "Updating product with id: $id" }
        return productRepository.findById(id)
            ?.let {
                log.debug { "Product found for update, proceeding with update for id: $id" }
                productRequest.toEntity(it)
            }
            ?.let { productRepository.save(it) }
            ?.toResponse()
    }

    @Transactional
    override suspend fun delete(id: Long): Boolean {
        log.debug { "Attempting to delete product with id: $id" }
        return if (productRepository.existsById(id)) {
            productRepository.deleteById(id)
            log.debug { "Product deleted successfully with id: $id" }
            true
        } else {
            log.debug { "Product not found for deletion with id: $id" }
            false
        }
    }

    /**
     * Retrieves a paginated list of products based on the provided pagination information.
     *
     * @param pageable the pagination information, including the page number and page size
     * @return a ProductPageResponse containing the list of products for the current page,
     *         along with pagination metadata such as total elements, total pages, and page details
     */
    @Transactional(readOnly = true)
    override suspend fun getPagedProducts(pageable: Pageable): ProductPageResponse {
        log.debug { "Retrieving paged products: page=${pageable.pageNumber}, size=${pageable.pageSize}" }
        val products = findAll(pageable).toList()
        val totalElements = productRepository.count()
        val totalPages = ((totalElements + pageable.pageSize - 1) / pageable.pageSize).toInt()

        val response = ProductPageResponse(
            content = products,
            page = pageable.pageNumber,
            size = pageable.pageSize,
            totalElements = totalElements,
            totalPages = totalPages,
            first = pageable.pageNumber == FIRST_PAGE_INDEX,
            last = pageable.pageNumber >= totalPages - PAGE_CALCULATION_OFFSET,
        )

        log.debug { "Paged products retrieved: ${products.size} items on page ${pageable.pageNumber} of $totalPages total pages" }
        return response
    }

    @Transactional
    override suspend fun importProducts(file: FilePart) {
        log.info { "Starting product import: ${file.filename()}" }

        var imported = 0
        var skipped = 0
        val existingGoldIds = mutableSetOf<Long>()

        try {
            // Parse CSV and process in batches for memory efficiency
            file.content()
                .asFlow()
                .let { parseCsvLines(it) }
                .buffer(DEFAULT_BATCH_SIZE)
                .onEach { products ->
                    // Filter out duplicates from current batch
                    val candidateProducts = products.filter { product ->
                        !existingGoldIds.contains(product.goldId)
                    }

                    if (candidateProducts.isEmpty()) {
                        skipped += products.size
                        return@onEach
                    }

                    // Batch check for existing goldIds in database
                    val goldIds = candidateProducts.map { it.goldId }
                    val existingDbGoldIds = productRepository.findByGoldIdIn(goldIds)
                        .map { it.goldId }
                        .toList()
                        .toSet()

                    // Filter out products that already exist in database
                    val batch = candidateProducts.filter { product ->
                        if (existingDbGoldIds.contains(product.goldId)) {
                            skipped++
                            false
                        } else {
                            existingGoldIds.add(product.goldId)
                            imported++
                            true
                        }
                    }

                    if (batch.isNotEmpty()) {
                        productRepository.saveAll(batch).collect()
                        log.debug { "Imported batch of ${batch.size} products, skipped ${products.size - batch.size}" }
                    }
                }
                .onCompletion { exception ->
                    if (exception != null) {
                        log.error(exception) { "Error during product import" }
                    } else {
                        log.info { "Product import completed: imported=$imported, skipped=$skipped" }
                    }
                }
                .collect()

        } catch (e: Exception) {
            log.error(e) { "Failed to import products from file: ${file.filename()}" }
            throw e
        }
    }

    private fun parseCsvLines(dataBufferFlow: Flow<DataBuffer>): Flow<List<Product>> = flow {
        var headerParsed = false
        var buffer = ""
        val currentBatch = mutableListOf<Product>()

        dataBufferFlow.collect { dataBuffer ->
            val chunk = dataBuffer.toString(StandardCharsets.UTF_8)
            buffer += chunk

            val lines = buffer.split('\n')
            buffer = lines.last() // Keep incomplete line in buffer

            for (i in 0 until lines.size - 1) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue

                if (!headerParsed) {
                    headerParsed = true
                    continue // Skip header row
                }

                try {
                    val product = parseCsvLine(line)
                    currentBatch.add(product)

                    if (currentBatch.size >= DEFAULT_BATCH_SIZE) {
                        emit(currentBatch.toList())
                        currentBatch.clear()
                    }
                } catch (e: Exception) {
                    log.warn(e) { "Failed to parse CSV line: $line" }
                }
            }
        }

        // Process remaining buffer if any
        if (buffer.trim().isNotEmpty() && headerParsed) {
            try {
                val product = parseCsvLine(buffer.trim())
                currentBatch.add(product)
            } catch (e: Exception) {
                log.warn(e) { "Failed to parse final CSV line: $buffer" }
            }
        }

        // Emit remaining products
        if (currentBatch.isNotEmpty()) {
            emit(currentBatch.toList())
        }
    }

    private fun parseCsvLine(line: String): Product {
        val fields = parseCsvFields(line)
        require(fields.size == 6) { "Expected 6 fields, got ${fields.size}" }

        return Product(
            uuid = UUID.fromString(fields[0]),
            goldId = fields[1].toLong(),
            longName = fields[2],
            shortName = fields[3],
            iowUnitType = fields[4],
            healthyCategory = fields[5],
        )
    }
    
    /**
     * Parses a single line of CSV-formatted text into a list of fields, handling quoted fields
     * and escaped quotes within those fields.
     *
     * @param line the CSV line to be parsed as a string
     * @return a list of strings representing the parsed fields of the line
     */
    private fun parseCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && !inQuotes && (i == 0 || line[i - 1] == ',') -> {
                    // Start of quoted field
                    inQuotes = true
                }

                char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    // Escaped quote within quoted field
                    current.append('"')
                    i++ // Skip the next quote
                }

                char == '"' && inQuotes && (i == line.length - 1 || line[i + 1] == ',') -> {
                    // End of quoted field
                    inQuotes = false
                }

                char == ',' && !inQuotes -> {
                    // Field separator
                    fields.add(current.toString())
                    current = StringBuilder()
                }

                else -> {
                    // Regular character or quoted character
                    if (!(char == '"' && !inQuotes && (i == 0 || line[i - 1] == ','))) {
                        current.append(char)
                    }
                }
            }
            i++
        }

        fields.add(current.toString())
        return fields
    }
}
