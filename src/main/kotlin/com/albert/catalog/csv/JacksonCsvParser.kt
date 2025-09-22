package com.albert.catalog.csv

import com.albert.catalog.entity.Product
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import java.io.StringReader
import java.nio.charset.StandardCharsets

@Component
class JacksonCsvParser {

    companion object {
        private val log = KotlinLogging.logger {}
        private val csvMapper = CsvMapper().apply {
            registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullToEmptyCollection, false)
                    .configure(KotlinFeature.NullToEmptyMap, false)
                    .configure(KotlinFeature.NullIsSameAsDefault, false)
                    .configure(KotlinFeature.SingletonSupport, false)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build(),
            )

            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
        }

        private val csvSchema = CsvSchema.builder()
            .addColumn("uuid")
            .addColumn("gold_id")
            .addColumn("long_name")
            .addColumn("short_name")
            .addColumn("iow_unit_type")
            .addColumn("healthy_category")
            .setUseHeader(true)
            .build()
    }

    /**
     * Parses a provided CSV file in a streaming manner and converts the entries into a flow of `Product` entities.
     * This method efficiently processes the file content in chunks to minimize memory usage, making it suitable
     * for handling large files.
     *
     * @param file the file to be parsed, represented as a `FilePart`. It contains the streamable
     *             content of the CSV file.
     * @return a flow of `Product` entities parsed from the CSV file.
     */
    fun parseFileStreaming(file: FilePart): Flow<Product> = flow {
        log.info { "Starting efficient streaming CSV parse for: ${file.filename()}" }

        val streamingState = StreamingParseState()

        file.content()
            .asFlow()
            .collect { dataBuffer ->
                processDataChunk(dataBuffer, streamingState) { product ->
                    emit(product)
                    streamingState.incrementProductCount()
                }
            }

        processRemainingContent(streamingState) { product ->
            emit(product)
            streamingState.incrementProductCount()
        }

        log.info { "Streaming parse completed. Parsed ${streamingState.productCount} products" }
    }.flowOn(Dispatchers.IO)

    /**
     * Represents the state during streaming CSV parsing
     */
    private data class StreamingParseState(
        val contentBuffer: StringBuilder = StringBuilder(),
        var productCount: Int = 0,
        var isHeaderProcessed: Boolean = false,
    ) {
        fun incrementProductCount() = productCount++
    }

    /**
     * Processes a single data chunk from the streaming content
     */
    private suspend fun processDataChunk(
        dataBuffer: DataBuffer,
        state: StreamingParseState,
        emitProduct: suspend (Product) -> Unit,
    ) {
        val chunk = extractChunkContent(dataBuffer)
        state.contentBuffer.append(chunk)

        extractCompleteLines(state.contentBuffer)
            .takeIf { it.isNotEmpty() }
            ?.let { completeContent ->
                parseRecordsBatch(completeContent, state.isHeaderProcessed)
                    .collect { product -> emitProduct(product) }
                state.isHeaderProcessed = true
            }
    }

    /**
     * Processes any remaining content in the buffer after streaming is complete
     */
    private suspend fun processRemainingContent(
        state: StreamingParseState,
        emitProduct: suspend (Product) -> Unit,
    ) {
        state.contentBuffer.toString()
            .trim()
            .takeIf { it.isNotEmpty() && state.isHeaderProcessed }
            ?.let { remainingContent ->
                buildCsvWithHeader(remainingContent)
                    .let { headerContent ->
                        parseRecordsBatch(headerContent, skipHeader = true)
                            .collect { product -> emitProduct(product) }
                    }
            }
    }

    /**
     * Builds CSV content with header for remaining data
     */
    private fun buildCsvWithHeader(remainingContent: String): String =
        "uuid,gold_id,long_name,short_name,iow_unit_type,healthy_category\n$remainingContent"

    /**
     * Extract complete lines from buffer, keeping incomplete line for next chunk
     */
    private fun extractCompleteLines(buffer: StringBuilder): String =
        buffer.toString().let { content ->
            content.lines().let { lines ->
                processCompleteLines(content, lines, buffer)
            }
        }

    /**
     * Processes complete lines and manages buffer state
     */
    private fun processCompleteLines(content: String, lines: List<String>, buffer: StringBuilder): String =
        when {
            content.endsWithNewline() -> lines
            else -> handleIncompleteLines(lines, buffer)
        }.let { completeLines ->
            completeLines.takeIf { it.isNotEmpty() }
                ?.also { manageBufferState(content, buffer) }
                ?.joinToString("\n")
                ?: ""
        }

    /**
     * Handles lines when content doesn't end with newline
     */
    private fun handleIncompleteLines(lines: List<String>, buffer: StringBuilder): List<String> =
        lines.lastOrNull()
            ?.also { incompleteLine ->
                buffer.clear()
                buffer.append(incompleteLine)
            }
            .let { lines.dropLast(1) }

    /**
     * Manages buffer clearing based on content state
     */
    private fun manageBufferState(content: String, buffer: StringBuilder) {
        if (!content.endsWithNewline()) {
            buffer.clear()
        }
    }

    /**
     * Extension function to check if string ends with newline
     */
    private fun String.endsWithNewline(): Boolean = endsWith("\n") || endsWith("\r\n")

    /**
     * Parses a batch of CSV content and emits `Product` entities as a flow.
     * This function processes the CSV content in a streaming manner, allowing for efficient batch validation
     * and avoiding memory overloading. Invalid records are skipped with a warning logged,
     * while parsing continues with the remaining records.
     *
     * @param csvContent the CSV content to parse, represented as a string.
     * @param skipHeader a flag indicating whether the header in the CSV content should be skipped.
     *                   Useful if headers were already processed in previous batches.
     * @return a flow of `Product` entities parsed from the given CSV content.
     */
    private fun parseRecordsBatch(csvContent: String, skipHeader: Boolean): Flow<Product> = flow {
        csvContent.takeUnless { it.isBlank() }
            ?.let { content ->
                parseCsvContent(content, skipHeader) { product ->
                    emit(product)
                }
            }
    }

    /**
     * Parses CSV content and processes products with the given emit function
     */
    private suspend fun parseCsvContent(
        csvContent: String,
        skipHeader: Boolean,
        emitProduct: suspend (Product) -> Unit,
    ) {
        runCatching {
            StringReader(csvContent).use { reader ->
                createCsvIterator(reader)
                    .also { iterator -> skipHeaderIfNeeded(iterator, skipHeader) }
                    .let { iterator -> processAllRecords(iterator, emitProduct) }
            }
        }.onFailure { exception ->
            log.error { "Failed to parse CSV batch: ${exception.message}" }
        }
    }

    /**
     * Creates CSV mapping iterator for Product parsing
     */
    private fun createCsvIterator(reader: StringReader): MappingIterator<Product> =
        csvMapper
            .readerFor(Product::class.java)
            .with(csvSchema)
            .readValues(reader)

    /**
     * Skips header if needed and iterator has content
     */
    private fun skipHeaderIfNeeded(iterator: MappingIterator<Product>, skipHeader: Boolean) {
        if (skipHeader && iterator.hasNext()) {
            iterator.next()
        }
    }

    /**
     * Processes all records from iterator, handling individual record errors
     */
    private suspend fun processAllRecords(
        iterator: MappingIterator<Product>,
        emitProduct: suspend (Product) -> Unit,
    ) {
        while (iterator.hasNext()) {
            runCatching { iterator.next() }
                .onSuccess { product -> emitProduct(product) }
                .onFailure { exception ->
                    log.warn { "Failed to parse CSV record: ${exception.message}" }
                }
        }
    }

    /**
     * Extract content from DataBuffer and release it
     */
    private fun extractChunkContent(dataBuffer: DataBuffer): String =
        try {
            val bytes = ByteArray(dataBuffer.readableByteCount())
            dataBuffer.read(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            DataBufferUtils.release(dataBuffer)
        }
}
