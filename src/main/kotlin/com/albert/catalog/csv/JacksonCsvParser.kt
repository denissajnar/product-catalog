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
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import java.io.StringReader
import java.nio.charset.StandardCharsets

@Component
class JacksonCsvParser {

    companion object {
        private val log = KotlinLogging.logger {}

        // Configure Jackson CSV mapper
        private val csvMapper = CsvMapper().apply {
            // Register Kotlin module for better Kotlin support
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

            // Configure for lenient parsing
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
        }

        // Define CSV schema - matches your CSV headers
        private val csvSchema = CsvSchema.builder()
            .addColumn("uuid")
            .addColumn("gold_id")
            .addColumn("long_name")
            .addColumn("short_name")
            .addColumn("iow_unit_type")
            .addColumn("healthy_category")
            .setUseHeader(true) // First row is header
            .build()
    }

    /**
     * Efficient streaming CSV parser that processes records in batches
     * instead of creating new readers/iterators for each line
     */
    fun parseFileStreaming(file: FilePart): Flow<Product> = flow {
        log.info { "Starting efficient streaming CSV parse for: ${file.filename()}" }

        val contentBuffer = StringBuilder()
        var productCount = 0
        var isHeaderProcessed = false

        file.content()
            .asFlow()
            .collect { dataBuffer ->
                val chunk = extractChunkContent(dataBuffer)
                contentBuffer.append(chunk)

                // Process complete lines from buffer
                val completeContent = extractCompleteLines(contentBuffer)

                if (completeContent.isNotEmpty()) {
                    // Parse batch of records from complete content
                    parseRecordsBatch(completeContent, isHeaderProcessed).collect { product ->
                        emit(product)
                        productCount++
                    }
                    isHeaderProcessed = true
                }
            }

        // Process any remaining content in buffer
        val remainingContent = contentBuffer.toString().trim()
        if (remainingContent.isNotEmpty() && isHeaderProcessed) {
            // Add header for remaining data lines
            val headerContent = "uuid,gold_id,long_name,short_name,iow_unit_type,healthy_category\n$remainingContent"

            parseRecordsBatch(headerContent, true).collect { product ->
                emit(product)
                productCount++
            }
        }

        log.info { "Streaming parse completed. Parsed $productCount products" }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract complete lines from buffer, keeping incomplete line for next chunk
     */
    private fun extractCompleteLines(buffer: StringBuilder): String {
        val content = buffer.toString()
        val lines = content.lines()

        // If content doesn't end with newline, last line is incomplete
        val completeLines = if (content.endsWith("\n") || content.endsWith("\r\n")) {
            lines
        } else {
            // Keep incomplete line in buffer for next chunk
            val incompleteLine = lines.lastOrNull() ?: ""
            buffer.clear()
            buffer.append(incompleteLine)
            lines.dropLast(1)
        }

        // Return complete lines as CSV content
        return if (completeLines.isNotEmpty()) {
            if (!content.endsWith("\n") && !content.endsWith("\r\n")) {
                buffer.clear() // Clear buffer since we're processing complete lines
            }
            completeLines.joinToString("\n")
        } else {
            ""
        }
    }

    /**
     * Parse a batch of CSV records efficiently using a single MappingIterator
     */
    private fun parseRecordsBatch(csvContent: String, skipHeader: Boolean): Flow<Product> = flow {
        if (csvContent.isBlank()) return@flow

        try {
            StringReader(csvContent).use { reader ->
                val iterator: MappingIterator<Product> = csvMapper
                    .readerFor(Product::class.java)
                    .with(csvSchema)
                    .readValues(reader)

                // Skip header if already processed
                if (skipHeader && iterator.hasNext()) {
                    iterator.next() // Skip header record
                }

                // Process all records in this batch
                while (iterator.hasNext()) {
                    try {
                        val product = iterator.next()
                        emit(product)
                    } catch (e: Exception) {
                        log.warn { "Failed to parse CSV record: ${e.message}" }
                        // Continue processing other records
                    }
                }
            }
        } catch (e: Exception) {
            log.error { "Failed to parse CSV batch: ${e.message}" }
        }
    }

    /**
     * Extract content from DataBuffer and release it
     */
    private fun extractChunkContent(dataBuffer: org.springframework.core.io.buffer.DataBuffer): String {
        return try {
            val bytes = ByteArray(dataBuffer.readableByteCount())
            dataBuffer.read(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            DataBufferUtils.release(dataBuffer)
        }
    }
}
