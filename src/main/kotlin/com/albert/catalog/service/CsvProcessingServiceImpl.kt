package com.albert.catalog.service

import com.albert.catalog.entity.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Implementation of CSV processing service that handles streaming CSV parsing
 * with batching for memory efficiency and proper handling of quoted fields.
 */
@Service
class CsvProcessingServiceImpl : CsvProcessingService {

    companion object {
        private val log = KotlinLogging.logger {}
        const val DEFAULT_BATCH_SIZE = 1000
    }

    override fun parseProductsFromCsv(file: FilePart): Flow<List<Product>> =
        file.content()
            .asFlow()
            .let { parseCsvLines(it) }

    /**
     * Parses a stream of CSV data buffers into a flow of product batches. The first row of the CSV
     * is treated as a header and skipped during processing. Each batch contains a predefined number
     * of parsed products, unless the stream ends and a smaller batch remains.
     *
     * @param dataBufferFlow A flow of DataBuffer objects representing chunks of CSV data.
     * @return A flow emitting lists of Product objects parsed from the CSV rows. Each list represents a batch.
     */
    private fun parseCsvLines(dataBufferFlow: Flow<DataBuffer>): Flow<List<Product>> = flow {
        var headerParsed = false
        var buffer = ""
        val currentBatch = mutableListOf<Product>()

        dataBufferFlow.collect { dataBuffer ->
            buffer = processDataChunk(dataBuffer, buffer, currentBatch, headerParsed) { shouldSkipHeader ->
                headerParsed = shouldSkipHeader
            }
        }

        processFinalBuffer(buffer, headerParsed, currentBatch)

        if (currentBatch.isNotEmpty()) {
            emit(currentBatch.toList())
        }
    }

    /**
     * Processes a chunk of data from the given buffer, separates it into lines, processes
     * each line, and appends incomplete lines back to the buffer for subsequent processing.
     * Handles header parsing and manages batching of `Product` objects.
     *
     * @param dataBuffer The current chunk of data to process, represented as a `DataBuffer`.
     * @param initialBuffer The initial buffer string, carrying over any incomplete data from the previous chunk.
     * @param currentBatch A mutable list of `Product` objects to which the processed data is added.
     * @param headerParsed A flag indicating whether the header has already been parsed in previous chunks.
     * @param onHeaderParsed A callback to update the header parsed status when the header is encountered.
     * @return The remaining buffer containing any incomplete line data.
     */
    private suspend fun FlowCollector<List<Product>>.processDataChunk(
        dataBuffer: DataBuffer,
        initialBuffer: String,
        currentBatch: MutableList<Product>,
        headerParsed: Boolean,
        onHeaderParsed: (Boolean) -> Unit,
    ): String {
        val chunk = dataBuffer.toString(StandardCharsets.UTF_8)
        val buffer = initialBuffer + chunk
        val lines = buffer.split('\n')
        val remainingBuffer = lines.last()

        var skipHeader = !headerParsed

        for (i in 0 until lines.size - 1) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            if (skipHeader) {
                skipHeader = false
                onHeaderParsed(true)
                continue
            }

            processLine(line, currentBatch)
            emitBatchIfReady(currentBatch)
        }

        return remainingBuffer
    }

    /**
     * Processes a single line of CSV input, parses it into a Product,
     * and adds it to the given batch. Logs a warning in case of a parsing failure.
     *
     * @param line The CSV line to be processed.
     * @param currentBatch The list of products to which the parsed product will be added.
     */
    private suspend fun processLine(
        line: String,
        currentBatch: MutableList<Product>,
    ) {
        try {
            val product = parseCsvLine(line)
            currentBatch.add(product)
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse CSV line: $line" }
        }
    }

    /**
     * Processes the final buffer, parsing and adding the resulting product to the current batch if the buffer
     * contains valid data and the header has already been parsed.
     *
     * @param buffer The string containing the raw CSV line to be processed.
     * @param headerParsed Indicates whether the header has already been parsed. Parsing proceeds only if this is true.
     * @param currentBatch A mutable list of Product objects to which the parsed product will be added.
     */
    private fun processFinalBuffer(
        buffer: String,
        headerParsed: Boolean,
        currentBatch: MutableList<Product>,
    ) {
        if (buffer.trim().isNotEmpty() && headerParsed) {
            try {
                val product = parseCsvLine(buffer.trim())
                currentBatch.add(product)
            } catch (e: Exception) {
                log.warn(e) { "Failed to parse final CSV line: $buffer" }
            }
        }
    }

    /**
     * Emits the current batch of products to the flow collector if the batch size reaches the default threshold.
     * After emission, the batch is cleared.
     *
     * @param currentBatch A mutable list of products representing the current batch to be emitted if ready.
     */
    private suspend fun FlowCollector<List<Product>>.emitBatchIfReady(currentBatch: MutableList<Product>) {
        if (currentBatch.size >= DEFAULT_BATCH_SIZE) {
            emit(currentBatch.toList())
            currentBatch.clear()
        }
    }

    /**
     * Parses a single CSV line into a Product entity.
     *
     * @param line The CSV line to parse
     * @return A Product entity parsed from the CSV line
     * @throws IllegalArgumentException if the line doesn't have the expected number of fields
     */
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
                char == '"' && isStartOfQuotedField(line, i, inQuotes) -> {
                    inQuotes = true
                }

                char == '"' && isEscapedQuote(line, i, inQuotes) -> {
                    current.append('"')
                    i++
                }

                char == '"' && isEndOfQuotedField(line, i, inQuotes) -> {
                    inQuotes = false
                }

                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current = StringBuilder()
                }

                shouldAppendCharacter(char, line, i, inQuotes) -> {
                    current.append(char)
                }
            }
            i++
        }

        fields.add(current.toString())
        return fields
    }

    /**
     * Determines if a quote character marks the start of a quoted field.
     */
    private fun isStartOfQuotedField(line: String, position: Int, inQuotes: Boolean) =
        !inQuotes && (position == 0 || line[position - 1] == ',')

    /**
     * Determines if a quote character is an escaped quote (double quote within a quoted field).
     */
    private fun isEscapedQuote(line: String, position: Int, inQuotes: Boolean) =
        inQuotes && position + 1 < line.length && line[position + 1] == '"'

    /**
     * Determines if a quote character marks the end of a quoted field.
     */
    private fun isEndOfQuotedField(line: String, position: Int, inQuotes: Boolean) =
        inQuotes && (position == line.length - 1 || line[position + 1] == ',')

    /**
     * Determines if a character should be appended to the current field being built.
     */
    private fun shouldAppendCharacter(char: Char, line: String, position: Int, inQuotes: Boolean) =
        !(char == '"' && !inQuotes && (position == 0 || line[position - 1] == ','))
}
