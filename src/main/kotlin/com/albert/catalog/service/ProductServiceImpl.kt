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
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val csvService: CsvService,
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
        return productRepository.findByIdOrNull(id)?.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["products"], key = "#id")
    override fun update(id: Long, productRequest: ProductRequest): ProductResponse? {
        log.debug { "Updating product with id: $id" }
        return productRepository.findByIdOrNull(id)
            ?.let { existingProduct ->
                log.debug { "Product found for update, proceeding with update for id: $id" }
                productRequest.toEntity(existingProduct)
            }
            ?.let { productRepository.save(it) }
            ?.toResponse()
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


    /**
     * Imports products from the provided CSV file. The method delegates CSV processing to CsvService
     * and handles product-specific business logic like duplicate filtering and database operations.
     *
     * @param file the CSV file containing product data to be imported. The file should be in UTF-8 encoding.
     */
    @Transactional
    override fun importProducts(file: MultipartFile) {
        log.info { "Starting product import: ${file.originalFilename}" }
        val stats = ImportStats()

        try {
            csvService.processProductsCsv(file) { products ->
                processProductBatch(products, stats)
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
     * Filters duplicate products based on their gold IDs by checking against
     * existing database records and updates import statistics accordingly.
     *
     * @param products The list of products to be filtered.
     * @param stats The statistics object used to track import and skip counts.
     * @return A list of products that are not duplicates in the database.
     */
    private fun filterDatabaseDuplicates(products: List<Product>, stats: ImportStats): List<Product> {
        if (products.isEmpty()) return products

        val goldIds = products.map { it.goldId }
        val existingDbProducts = productRepository.findByGoldIdIn(goldIds)
        val existingDbGoldIds = existingDbProducts.map { it.goldId }.toSet()

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
     * Processes a batch of products by filtering duplicates (both in-session and database-level)
     * and saving the valid products to the database.
     *
     * @param products The list of products to be processed in the batch.
     * @param stats The import statistics object to track skipped and imported products.
     */
    private fun processProductBatch(products: List<Product>, stats: ImportStats) {
        val originalBatchSize = products.size
        val candidateProducts = filterInSessionDuplicates(products, stats)

        if (candidateProducts.isEmpty()) {
            return
        }

        val productsToSave = filterDatabaseDuplicates(candidateProducts, stats)
        saveBatch(productsToSave, originalBatchSize)
    }

}
