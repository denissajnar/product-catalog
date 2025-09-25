package com.albert.catalog.service

import com.albert.catalog.dto.ImportStats
import com.albert.catalog.entity.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class CsvServiceImpl : CsvService {

    companion object {
        private val log = KotlinLogging.logger {}
        const val DEFAULT_BATCH_SIZE = 1000
    }

    override fun processProductsCsv(file: MultipartFile, onBatch: (List<Product>) -> Unit): ImportStats {
        log.info { "Starting CSV processing: ${file.originalFilename}" }
        val stats = ImportStats()

        try {
            BufferedReader(InputStreamReader(file.inputStream, StandardCharsets.UTF_8)).use { reader ->
                processFileLines(reader, stats, onBatch)
            }
            log.info { "CSV processing completed: ${file.originalFilename}, processed lines: ${stats.imported + stats.skipped}" }
        } catch (ex: Exception) {
            log.error(ex) { "CSV processing failed: ${file.originalFilename}" }
            throw RuntimeException("CSV processing failed: ${ex.message}", ex)
        }

        return stats
    }

    /**
     * Processes the lines of a file using the provided BufferedReader. Skips the first line
     * (assumed to be a header), processes each subsequent line, and manages the collected data in batches.
     *
     * @param reader the BufferedReader instance used to read the file lines
     * @param stats the ImportStats object to track processing statistics
     * @param onBatch callback function to handle each batch of products
     */
    private fun processFileLines(reader: BufferedReader, stats: ImportStats, onBatch: (List<Product>) -> Unit) {
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
                processLine(line, lineIndex, products, stats, onBatch)
                lineIndex++
            }

            handleBatch(products, onBatch)
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
     * @param onBatch callback function to handle each batch of products
     */
    private fun processLine(
        line: String,
        lineIndex: Int,
        products: MutableList<Product>,
        stats: ImportStats,
        onBatch: (List<Product>) -> Unit,
    ) {
        if (line.isBlank()) {
            return
        }

        try {
            val product = parseCsvLine(line)
            products.add(product)

            if (products.size >= DEFAULT_BATCH_SIZE) {
                handleBatch(products, onBatch)
            }
        } catch (ex: Exception) {
            log.error(ex) { "Error parsing line ${lineIndex + 2}: $line" }
            stats.incrementSkipped()
        }
    }

    private fun handleBatch(products: MutableList<Product>, onBatch: (List<Product>) -> Unit) {
        if (products.isNotEmpty()) {
            onBatch(products.toList())
            products.clear()
        }
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
