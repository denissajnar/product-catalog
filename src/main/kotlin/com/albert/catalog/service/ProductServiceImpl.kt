package com.albert.catalog.service

import com.albert.catalog.csv.SimpleFlatMapperParser
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
    private val simpleFlatMapperParser: SimpleFlatMapperParser,
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
     * Imports products from a CSV file and saves them in batches to the database.
     * The method processes the file contents in streaming mode to handle large files efficiently.
     * If the file contains more products than the specified batch size, the products are divided into smaller batches
     * and saved to the database incrementally.
     *
     * @param file The file containing product information to be imported. Expected to be in CSV format.
     */
    @Transactional
    override suspend fun importProducts(file: FilePart) {
        val products = mutableListOf<Product>()
        var totalProcessed = 0
        var batchCount = 0

        log.info { "Starting product import with batch size: $DEFAULT_BATCH_SIZE" }

        simpleFlatMapperParser.parseFileStreaming(file)
            .collect { product ->
                products.add(product)
                totalProcessed++

                if (products.size >= DEFAULT_BATCH_SIZE) {
                    batchCount++
                    log.info { "Processing batch $batchCount with ${products.size} products" }
                    productRepository.saveAll(products).toList()
                    products.clear()
                    log.debug { "Batch $batchCount saved successfully. Total processed so far: $totalProcessed" }
                }
            }

        if (products.isNotEmpty()) {
            batchCount++
            log.info { "Processing final batch $batchCount with ${products.size} products" }
            productRepository.saveAll(products).toList()
            log.debug { "Final batch $batchCount saved successfully" }
        }

        log.info { "Product import completed. Total products processed: $totalProcessed in $batchCount batches" }
    }
}
