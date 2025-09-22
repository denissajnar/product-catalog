package com.albert.catalog.service

import com.albert.catalog.csv.JacksonCsvParser
import com.albert.catalog.dto.BatchResult
import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import com.albert.catalog.mapper.toEntity
import com.albert.catalog.mapper.toResponse
import com.albert.catalog.repository.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.data.domain.Pageable
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val csvParser: JacksonCsvParser,
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

    /**
     * Imports products from a file provided as input. The method processes the file in batches,
     * saving new products, updating existing ones, and counting any errors encountered during the process.
     *
     * @param file the CSV file containing product data to be imported
     */
    @Transactional
    override suspend fun importProducts(file: FilePart) {
        log.info { "Starting efficient product import with batch size: $DEFAULT_BATCH_SIZE" }

        var totalProcessed = 0
        var savedCount = 0
        var updatedCount = 0
        var errorCount = 0
        val batch = mutableListOf<Product>()

        csvParser.parseFileStreaming(file)
            .collect { product ->
                batch.add(product)

                if (batch.size >= DEFAULT_BATCH_SIZE) {
                    val batchNumber = (totalProcessed / DEFAULT_BATCH_SIZE) + 1
                    log.info { "Processing batch $batchNumber with ${batch.size} products" }

                    val batchResult = processBatch(batch.toList())

                    totalProcessed += batch.size
                    savedCount += batchResult.saved
                    updatedCount += batchResult.updated
                    errorCount += batchResult.errors

                    batch.clear()
                    log.debug { "Batch $batchNumber complete. Progress: saved=${batchResult.saved}, updated=${batchResult.updated}, errors=${batchResult.errors}" }
                }
            }

        // Process remaining products in the batch
        if (batch.isNotEmpty()) {
            val batchNumber = (totalProcessed / DEFAULT_BATCH_SIZE) + 1
            log.info { "Processing final batch $batchNumber with ${batch.size} products" }

            val batchResult = processBatch(batch.toList())

            totalProcessed += batch.size
            savedCount += batchResult.saved
            updatedCount += batchResult.updated
            errorCount += batchResult.errors

            log.debug { "Final batch $batchNumber complete. Progress: saved=${batchResult.saved}, updated=${batchResult.updated}, errors=${batchResult.errors}" }
        }

        log.info {
            "Import completed: processed=$totalProcessed, " +
                    "saved=$savedCount, updated=$updatedCount, errors=$errorCount"
        }
    }

    /**
     * Processes a batch of products by either inserting new records or updating existing ones
     * based on their unique identifiers (goldId). Handles errors gracefully and provides
     * detailed results of the processing in a batch result.
     *
     * @param products A list of products to be processed. Each product is either inserted (if new)
     *                 or updated (if existing) based on its goldId.
     * @return A BatchResult object containing the counts of successfully saved, updated, and failed products.
     */
    @Transactional
    suspend fun processBatch(products: List<Product>): BatchResult {
        var saved = 0
        var updated = 0
        var errors = 0

        try {
            // Step 1: Bulk query existing products by goldId
            val goldIds = products.map { it.goldId }
            val existingProducts =
                productRepository.findByGoldIdIn(goldIds)
                    .toList()
                    .associateBy { it.goldId }

            // Step 2: Separate new products from updates
            val (updates, inserts) = products.partition { existingProducts.containsKey(it.goldId) }

            // Step 3: Process inserts in batch
            if (inserts.isNotEmpty()) {
                try {
                    productRepository.saveAll(inserts).toList()
                    saved += inserts.size
                    log.debug { "Batch inserted ${inserts.size} new products" }
                } catch (e: Exception) {
                    log.error { "Batch insert failed, processing individually: ${e.message}" }
                    // Fallback: process inserts individually
                    inserts.forEach { product ->
                        try {
                            productRepository.save(product)
                            saved++
                        } catch (ex: Exception) {
                            errors++
                            log.error { "Failed to save product with goldId ${product.goldId}: ${ex.message}" }
                        }
                    }
                }
            }

            // Step 4: Process updates in batch
            if (updates.isNotEmpty()) {
                val updatedProducts = updates.mapNotNull { newProduct ->
                    existingProducts[newProduct.goldId]?.copy(
                        longName = newProduct.longName,
                        shortName = newProduct.shortName,
                        iowUnitType = newProduct.iowUnitType,
                        healthyCategory = newProduct.healthyCategory,
                    )
                }

                try {
                    if (updatedProducts.isNotEmpty()) {
                        productRepository.saveAll(updatedProducts).toList()
                        updated += updatedProducts.size
                        log.debug { "Batch updated ${updatedProducts.size} existing products" }
                    }
                } catch (e: Exception) {
                    log.error { "Batch update failed, processing individually: ${e.message}" }
                    // Fallback: process updates individually
                    updatedProducts.forEach { product ->
                        try {
                            productRepository.save(product)
                            updated++
                        } catch (ex: Exception) {
                            errors++
                            log.error { "Failed to update product with goldId ${product.goldId}: ${ex.message}" }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            log.error { "Batch processing failed completely: ${e.message}" }
            errors += products.size
        }

        return BatchResult(saved, updated, errors)
    }
}
