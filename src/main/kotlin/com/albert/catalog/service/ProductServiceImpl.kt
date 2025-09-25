package com.albert.catalog.service

import com.albert.catalog.dto.ImportStats
import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import com.albert.catalog.mapper.toEntity
import com.albert.catalog.mapper.toResponse
import com.albert.catalog.repository.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.*
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val csvProcessingService: CsvProcessingService,
) : ProductService {

    companion object {
        private val log = KotlinLogging.logger {}
        const val DEFAULT_BATCH_SIZE = 1000
        const val FIRST_PAGE_INDEX = 0
        const val PAGE_CALCULATION_OFFSET = 1
    }

    /**
     * Filters out duplicate products from the batch based on already processed goldIds.
     *
     * @param products List of products to filter
     * @param stats Import statistics to track duplicates
     * @return List of products without duplicates from current session
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
     *
     * @param products List of products to check against database
     * @param stats Import statistics to track duplicates and successful imports
     * @return List of products that don't exist in database and are ready for import
     */
    private suspend fun filterDatabaseDuplicates(products: List<Product>, stats: ImportStats): List<Product> {
        if (products.isEmpty()) return products

        val goldIds = products.map { it.goldId }
        val existingDbGoldIds =
            productRepository.findByGoldIdIn(goldIds)
                .map { it.goldId }
                .toList()
                .toSet()

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
     *
     * @param products List of products to save
     * @param originalBatchSize Size of the original batch before filtering (for logging)
     */
    private suspend fun saveBatch(products: List<Product>, originalBatchSize: Int) {
        if (products.isNotEmpty()) {
            productRepository.saveAll(products).collect()
            log.debug { "Imported batch of ${products.size} products, skipped ${originalBatchSize - products.size}" }
        }
    }

    /**
     * Processes a single batch of products through the complete import pipeline:
     * filtering in-session duplicates, filtering database duplicates, and saving to database.
     *
     * @param products List of products in the current batch
     * @param stats Import statistics to track progress
     */
    private suspend fun processProductBatch(products: List<Product>, stats: ImportStats) {
        val originalBatchSize = products.size
        val candidateProducts = filterInSessionDuplicates(products, stats)

        if (candidateProducts.isEmpty()) {
            return
        }

        val productsToSave = filterDatabaseDuplicates(candidateProducts, stats)
        saveBatch(productsToSave, originalBatchSize)
    }

    override fun findAll(pageable: Pageable): Flow<ProductResponse> =
        productRepository.findAllBy(pageable)
            .map { it.toResponse() }

    @Cacheable(value = ["products"], key = "#id")
    override suspend fun findById(id: Long): ProductResponse? {
        log.debug { "Finding product by id: $id" }
        return productRepository.findById(id)?.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["products"], key = "#id")
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
    @CacheEvict(value = ["products"], key = "#id")
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
     * Imports products from the specified file. The file is expected to contain product data
     * in CSV format. The method processes the file in batches for memory efficiency and ensures
     * that duplicate or already existing products (based on their `goldId`) are skipped.
     *
     * @param file The file containing product data to be imported.
     */
    @Transactional
    override suspend fun importProducts(file: FilePart) {
        log.info { "Starting product import: ${file.filename()}" }
        val stats = ImportStats()

        try {
            csvProcessingService.parseProductsFromCsv(file)
                .buffer(DEFAULT_BATCH_SIZE)
                .onEach { products ->
                    processProductBatch(products, stats)
                }
                .onCompletion { exception ->
                    if (exception != null) {
                        log.error(exception) { "Error during product import" }
                    } else {
                        log.info { "Product import completed: imported=${stats.imported}, skipped=${stats.skipped}" }
                    }
                }
                .collect()

        } catch (e: Exception) {
            log.error(e) { "Failed to import products from file: ${file.filename()}" }
            throw e
        }
    }

}
