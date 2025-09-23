package com.albert.catalog.service

import com.albert.catalog.dto.ImportStats
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import com.albert.catalog.mapper.toEntity
import com.albert.catalog.mapper.toResponse
import com.albert.catalog.repository.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
) : ProductService {

    companion object {
        private val log = KotlinLogging.logger {}
        const val DEFAULT_BATCH_SIZE = 1000
    }

    override fun findAll(pageable: Pageable): List<ProductResponse> =
        productRepository.findAllBy(pageable).content.map { it.toResponse() }

    @Cacheable(value = ["products"], key = "#id")
    override fun findById(id: Long): ProductResponse? {
        log.debug { "Finding product by id: $id" }
        return productRepository.findById(id).orElse(null)?.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["products"], key = "#id")
    override fun update(id: Long, productRequest: ProductRequest): ProductResponse? {
        log.debug { "Updating product with id: $id" }
        return productRepository.findById(id)
            .map { existingProduct ->
                log.debug { "Product found for update, proceeding with update for id: $id" }
                productRequest.toEntity(existingProduct)
            }
            .map { productRepository.save(it) }
            .map { it.toResponse() }
            .orElse(null)
    }

    @Transactional
    @CacheEvict(value = ["products"], key = "#id")
    override fun delete(id: Long): Boolean {
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

    @Transactional(readOnly = true)
    override fun getPagedProducts(pageable: Pageable): Page<ProductResponse> {
        log.debug { "Retrieving paged products: page=${pageable.pageNumber}, size=${pageable.pageSize}" }
        val page = productRepository.findAllBy(pageable)
        val responsePage = page.map { it.toResponse() }

        log.debug { "Paged products retrieved: ${responsePage.content.size} items on page ${pageable.pageNumber} of ${page.totalPages} total pages" }
        return responsePage
    }

    @Transactional
    override fun importProducts(file: MultipartFile) {
        log.info { "Starting product import: ${file.originalFilename}" }
        val stats = ImportStats()

        try {
            BufferedReader(InputStreamReader(file.inputStream, StandardCharsets.UTF_8)).use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) {
                    log.warn { "File is empty: ${file.originalFilename}" }
                    return
                }

                // Skip header line and process data
                val dataLines = lines.drop(1)
                val products = mutableListOf<Product>()

                for ((index, line) in dataLines.withIndex()) {
                    try {
                        if (line.isNotBlank()) {
                            val product = parseCsvLine(line)
                            products.add(product)

                            // Process in batches
                            if (products.size >= DEFAULT_BATCH_SIZE || index == dataLines.size - 1) {
                                processProductBatch(products, stats)
                                products.clear()
                            }
                        }
                    } catch (ex: Exception) {
                        log.error(ex) { "Error parsing line ${index + 2}: $line" }
                        stats.incrementSkipped()
                    }
                }
            }

            log.info { "Product import completed: ${file.originalFilename}, imported: ${stats.imported}, skipped: ${stats.skipped}" }
        } catch (ex: Exception) {
            log.error(ex) { "Product import failed: ${file.originalFilename}" }
            throw RuntimeException("Import failed: ${ex.message}", ex)
        }
    }

    /**
     * Filters out duplicate products from the batch based on already processed goldIds.
     */
    private fun filterInSessionDuplicates(products: List<Product>, stats: ImportStats): List<Product> {
        val candidateProducts = products.filter { product ->
            !stats.existingGoldIds.contains(product.goldId)
        }

        if (candidateProducts.size < products.size) {
            stats.addSkipped(products.size - candidateProducts.size)
        }

        return candidateProducts
    }

    /**
     * Filters out products that already exist in the database based on goldId.
     */
    private fun filterDatabaseDuplicates(products: List<Product>, stats: ImportStats): List<Product> {
        if (products.isEmpty()) return products

        // Batch check for existing goldIds in database
        val goldIds = products.map { it.goldId }
        val existingDbProducts = productRepository.findByGoldIdIn(goldIds)
        val existingDbGoldIds = existingDbProducts.map { it.goldId }.toSet()

        // Filter out products that already exist in database
        return products.filter { product ->
            if (existingDbGoldIds.contains(product.goldId)) {
                stats.incrementSkipped()
                false
            } else {
                stats.existingGoldIds.add(product.goldId)
                stats.incrementImported()
                true
            }
        }
    }

    /**
     * Saves a batch of products to the database.
     */
    private fun saveBatch(products: List<Product>, originalBatchSize: Int) {
        if (products.isNotEmpty()) {
            productRepository.saveAll(products)
            log.debug { "Imported batch of ${products.size} products, skipped ${originalBatchSize - products.size}" }
        }
    }

    /**
     * Processes a single batch of products through the complete import pipeline.
     */
    private fun processProductBatch(products: List<Product>, stats: ImportStats) {
        val originalBatchSize = products.size

        // Filter out duplicates from current session
        val candidateProducts = filterInSessionDuplicates(products, stats)

        if (candidateProducts.isEmpty()) {
            return
        }

        // Filter out products that already exist in database and save the remaining ones
        val productsToSave = filterDatabaseDuplicates(candidateProducts, stats)
        saveBatch(productsToSave, originalBatchSize)
    }

    /**
     * Parses a single CSV line into a Product entity.
     */
    private fun parseCsvLine(line: String): Product {
        log.debug { "Parsing CSV line: $line" }
        val fields = parseCsvFields(line)

        if (fields.size < 6) {
            throw IllegalArgumentException("Invalid CSV format - expected 6 fields, got ${fields.size}")
        }

        return Product(
            uuid = UUID.fromString(fields[0].trim()),
            goldId = fields[1].trim().toLongOrNull() ?: throw IllegalArgumentException("Invalid goldId: ${fields[1]}"),
            longName = fields[2].trim(),
            shortName = fields[3].trim(),
            iowUnitType = fields[4].trim(),
            healthyCategory = fields[5].trim(),
        )
    }

    /**
     * Parses CSV fields from a line, handling quoted fields and escaped quotes.
     */
    private fun parseCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                }

                char == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        currentField.append('"')
                        i++ // Skip next quote
                    } else {
                        // End of quoted field
                        inQuotes = false
                    }
                }

                char == ',' && !inQuotes -> {
                    fields.add(currentField.toString())
                    currentField.clear()
                }

                else -> {
                    currentField.append(char)
                }
            }
            i++
        }

        // Add the last field
        fields.add(currentField.toString())

        return fields
    }
}
