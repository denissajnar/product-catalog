package com.albert.catalog.service

import com.albert.catalog.csv.JacksonCsvParser
import com.albert.catalog.dto.*
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

        val importState = ImportState()

        csvParser.parseFileStreaming(file)
            .collect { product ->
                processProductForImport(product, importState)
            }

        processRemainingBatch(importState)
        logImportCompletion(importState)
    }

    /**
     * Processes a single product for import, handling batch processing when needed
     */
    private suspend fun processProductForImport(product: Product, state: ImportState) {
        state.batch.add(product)

        if (state.batch.size >= DEFAULT_BATCH_SIZE) {
            processBatchAndUpdateState(state)
        }
    }

    /**
     * Processes current batch and updates import state
     */
    private suspend fun processBatchAndUpdateState(state: ImportState) {
        val batchNumber = state.getBatchNumber()
        log.info { "Processing batch $batchNumber with ${state.batch.size} products" }

        processBatch(state.batch.toList())
            .also { batchResult ->
                state.totalProcessed += state.batch.size
                state.updateCounts(batchResult)
                state.batch.clear()
                logBatchCompletion(batchNumber, batchResult)
            }
    }

    /**
     * Processes any remaining products in the final batch
     */
    private suspend fun processRemainingBatch(state: ImportState) {
        state.batch.takeIf { it.isNotEmpty() }
            ?.let {
                val batchNumber = state.getBatchNumber()
                log.info { "Processing final batch $batchNumber with ${state.batch.size} products" }

                processBatch(state.batch.toList())
                    .also { batchResult ->
                        state.totalProcessed += state.batch.size
                        state.updateCounts(batchResult)
                        logBatchCompletion(batchNumber, batchResult, isFinal = true)
                    }
            }
    }

    /**
     * Logs batch completion with results
     */
    private fun logBatchCompletion(batchNumber: Int, batchResult: BatchResult, isFinal: Boolean = false) {
        val batchType = if (isFinal) "Final batch" else "Batch"
        log.debug { "$batchType $batchNumber complete. Progress: saved=${batchResult.saved}, updated=${batchResult.updated}, errors=${batchResult.errors}" }
    }

    /**
     * Logs import completion summary
     */
    private fun logImportCompletion(state: ImportState) {
        log.info {
            "Import completed: processed=${state.totalProcessed}, " +
                    "saved=${state.savedCount}, updated=${state.updatedCount}, errors=${state.errorCount}"
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
    suspend fun processBatch(products: List<Product>): BatchResult =
        runCatching {
            val existingProducts = queryExistingProductsByGoldId(products)
            val (updates, inserts) = partitionProductsForProcessing(products, existingProducts)

            val savedCount = processProductInserts(inserts)
            val updatedCount = processProductUpdates(updates, existingProducts)

            BatchResult(savedCount, updatedCount, 0)
        }.getOrElse { exception ->
            log.error { "Batch processing failed completely: ${exception.message}" }
            BatchResult(0, 0, products.size)
        }

    /**
     * Queries existing products by their gold IDs using functional approach
     */
    private suspend fun queryExistingProductsByGoldId(products: List<Product>): Map<Long, Product> =
        products.map { it.goldId }
            .let { goldIds ->
                productRepository.findByGoldIdIn(goldIds)
                    .toList()
                    .associateBy { product -> product.goldId }
            }

    /**
     * Partitions products into updates and inserts based on existing products
     */
    private fun partitionProductsForProcessing(
        products: List<Product>,
        existingProducts: Map<Long, Product>,
    ): Pair<List<Product>, List<Product>> =
        products.partition { existingProducts.containsKey(it.goldId) }

    /**
     * Processes product inserts with batch operation and individual fallback
     */
    private suspend fun processProductInserts(inserts: List<Product>): Int =
        inserts.takeIf { it.isNotEmpty() }
            ?.let { products ->
                runCatching {
                    productRepository.saveAll(products).toList()
                        .also { log.debug { "Batch inserted ${products.size} new products" } }
                        .size
                }.getOrElse { exception ->
                    log.error { "Batch insert failed, processing individually: ${exception.message}" }
                    processIndividualInserts(products)
                }
            } ?: 0

    /**
     * Processes inserts individually when batch operation fails
     */
    private suspend fun processIndividualInserts(products: List<Product>): Int =
        products.fold(0) { savedCount, product ->
            runCatching {
                productRepository.save(product)
                savedCount + 1
            }.getOrElse { exception ->
                log.error { "Failed to save product with goldId ${product.goldId}: ${exception.message}" }
                savedCount
            }
        }

    /**
     * Processes product updates with batch operation and individual fallback
     */
    private suspend fun processProductUpdates(
        updates: List<Product>,
        existingProducts: Map<Long, Product>,
    ): Int =
        updates.takeIf { it.isNotEmpty() }
            ?.let { products ->
                buildUpdatedProducts(products, existingProducts)
                    .takeIf { it.isNotEmpty() }
                    ?.let { updatedProducts ->
                        runCatching {
                            productRepository.saveAll(updatedProducts).toList()
                                .also { log.debug { "Batch updated ${updatedProducts.size} existing products" } }
                                .size
                        }.getOrElse { exception ->
                            log.error { "Batch update failed, processing individually: ${exception.message}" }
                            processIndividualUpdates(updatedProducts)
                        }
                    } ?: 0
            } ?: 0

    /**
     * Builds updated product entities from new product data using functional approach
     */
    private fun buildUpdatedProducts(
        updates: List<Product>,
        existingProducts: Map<Long, Product>,
    ): List<Product> =
        updates.mapNotNull { newProduct ->
            existingProducts[newProduct.goldId]?.copy(
                longName = newProduct.longName,
                shortName = newProduct.shortName,
                iowUnitType = newProduct.iowUnitType,
                healthyCategory = newProduct.healthyCategory,
            )
        }

    /**
     * Processes updates individually when batch operation fails
     */
    private suspend fun processIndividualUpdates(products: List<Product>): Int =
        products.fold(0) { updatedCount, product ->
            runCatching {
                productRepository.save(product)
                updatedCount + 1
            }.getOrElse { exception ->
                log.error { "Failed to update product with goldId ${product.goldId}: ${exception.message}" }
                updatedCount
            }
        }
}
