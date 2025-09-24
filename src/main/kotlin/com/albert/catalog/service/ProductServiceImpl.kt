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
     * Imports products from the provided CSV file. The method processes the file line by line,
     * parses CSV data to create product objects, and stores them in batches. It also logs
     * the import statistics, including successfully imported and skipped records, and handles errors.
     *
     * @param file the CSV file containing product data to be imported. The file should be in UTF-8 encoding.
     */
    @Transactional
    override fun importProducts(file: MultipartFile) {
        log.info { "Starting product import: ${file.originalFilename}" }
        val stats = ImportStats()

        try {
            BufferedReader(InputStreamReader(file.inputStream, StandardCharsets.UTF_8)).use { reader ->
                processFileLines(reader, stats)
            }
            log.info { "Product import completed: ${file.originalFilename}, imported: ${stats.imported}, skipped: ${stats.skipped}" }
        } catch (ex: Exception) {
            log.error(ex) { "Product import failed: ${file.originalFilename}" }
            throw RuntimeException("Import failed: ${ex.message}", ex)
        }
    }

    /**
     * Processes the lines of a file using the provided BufferedReader. Skips the first line
     * (assumed to be a header), processes each subsequent line, and manages the collected data in batches.
     *
     * @param reader the BufferedReader instance used to read the file lines
     * @param stats the ImportStats object to track processing statistics
     */
    private fun processFileLines(reader: BufferedReader, stats: ImportStats) {
        reader.useLines { lines ->
            val lineIterator = lines.iterator()
            if (!lineIterator.hasNext()) {
                log.warn { "File is empty" }
                return
            }

            lineIterator.next()

            val products = mutableListOf<Product>()
            var lineIndex = 0

            while (lineIterator.hasNext()) {
                val line = lineIterator.next()
                processLine(line, lineIndex, products, stats)
                lineIndex++
            }

            handleBatch(products, stats)
        }
    }

    /**
     * Processes a single line from the input data. Parses the line into a `Product` object, adds it to the
     * product list, and triggers batch processing if the batch size limit is reached. Logs errors and updates
     * statistics for any failed parsing attempts.
     *
     * @param line the current line of input data to process
     * @param lineIndex the index of the current line being processed, used for error logging
     * @param products the list of `Product` objects to which the parsed product will be added
     * @param stats an `ImportStats` object used to track import statistics, including skipped lines
     */
    private fun processLine(line: String, lineIndex: Int, products: MutableList<Product>, stats: ImportStats) {
        if (line.isBlank()) {
            return
        }

        try {
            val product = parseCsvLine(line)
            products.add(product)

            if (products.size >= DEFAULT_BATCH_SIZE) {
                handleBatch(products, stats)
            }
        } catch (ex: Exception) {
            log.error(ex) { "Error parsing line ${lineIndex + 2}: $line" }
            stats.incrementSkipped()
        }
    }

    private fun handleBatch(products: MutableList<Product>, stats: ImportStats) {
        if (products.isNotEmpty()) {
            processProductBatch(products, stats)
            products.clear()
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

    /**
     * Parses a single CSV line into a Product entity.
     */
    private fun parseCsvLine(line: String): Product {
        log.debug { "Parsing CSV line: $line" }
        val fields = parseCsvFields(line)

        require(fields.size >= 6) { "Invalid CSV format - expected 6 fields, got ${fields.size}" }

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
     * Parses a single line of a CSV (Comma-Separated Values) file into a list of fields.
     * Handles quoted fields and escaped quotes within quoted fields.
     *
     * @param line The CSV-formatted string to parse.
     * @return A list of strings where each element corresponds to a field in the input CSV line.
     */
    private fun parseCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            val nextChar = if (i + 1 < line.length) line[i + 1] else null

            when {
                isStartQuote(char, inQuotes) -> {
                    inQuotes = true
                }

                isEscapedQuote(char, nextChar, inQuotes) -> {
                    currentField.append('"')
                    i++
                }

                isEndQuote(char, nextChar, inQuotes) -> {
                    inQuotes = false
                }

                isFieldSeparator(char, inQuotes) -> {
                    addFieldAndReset(fields, currentField)
                }

                else -> {
                    currentField.append(char)
                }
            }
            i++
        }

        fields.add(currentField.toString())
        return fields
    }

    private fun isStartQuote(char: Char, inQuotes: Boolean) =
        char == '"' && !inQuotes

    private fun isEscapedQuote(char: Char, nextChar: Char?, inQuotes: Boolean) =
        char == '"' && inQuotes && nextChar == '"'

    private fun isEndQuote(char: Char, nextChar: Char?, inQuotes: Boolean) =
        char == '"' && inQuotes && nextChar != '"'

    private fun isFieldSeparator(char: Char, inQuotes: Boolean) =
        char == ',' && !inQuotes

    private fun addFieldAndReset(fields: MutableList<String>, currentField: StringBuilder) {
        fields.add(currentField.toString())
        currentField.clear()
    }
}
