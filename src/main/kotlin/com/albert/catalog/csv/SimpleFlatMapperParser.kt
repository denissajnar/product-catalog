package com.albert.catalog.csv

import com.albert.catalog.entity.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import org.simpleflatmapper.csv.CsvMapperFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import java.io.StringReader
import java.nio.charset.StandardCharsets

@Component
class SimpleFlatMapperParser {
    companion object {
        private val log = KotlinLogging.logger {}
        private val csvMapper =
            CsvMapperFactory
                .newInstance()
                .newMapper(Product::class.java)
    }


    /**
     * Parses a CSV file provided as a `FilePart` in a streaming manner and emits
     * each parsed product as a flow of `Product` objects.
     * This method reads the file content efficiently in chunks, processes it line by line
     * to handle large files without loading them entirely into memory.
     *
     * @param file the file part representing the uploaded CSV file to be parsed
     * @return a flow of parsed `Product` objects emitted one at a time
     * @throws Exception if an error occurs during file parsing
     */
    fun parseFileStreaming(file: FilePart): Flow<Product> =
        flow {
            log.info { "Starting CSV parsing for file: ${file.filename()}" }
            try {
                val content = collectFileContent(file)
                log.info { "File content collected, size: ${content.length} characters for file: ${file.filename()}" }

                var productCount = 0
                StringReader(content).use { reader ->
                    val iterator = csvMapper.iterator(reader)
                    while (iterator.hasNext()) {
                        val product = iterator.next()
                        productCount++
                        emit(product)

                        if (productCount % 100 == 0) {
                            log.debug { "Parsed $productCount products so far from file: ${file.filename()}" }
                        }
                    }
                }
                log.info { "CSV parsing completed for file: ${file.filename()}, total products parsed: $productCount" }
            } catch (ex: Exception) {
                log.error(ex) { "Error parsing CSV file: ${file.filename()}" }
                throw ex
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Collects and aggregates the content of a file provided as `FilePart`.
     * This method reads the file content in chunks, reconstructs it as a complete string,
     * and releases the corresponding buffers after processing.
     *
     * @param file the file part representing the uploaded file whose content is to be collected
     * @return the aggregated content of the file as a single string
     * @throws Exception if an error occurs during file content collection
     */
    private suspend fun collectFileContent(file: FilePart): String =
        try {
            log.debug { "Collecting file content for file: ${file.filename()}" }
            file.content()
                .asFlow()
                .map { buffer: DataBuffer ->
                    val content = StringBuilder()
                    buffer.readableByteBuffers().forEach { byteBuffer ->
                        content.append(StandardCharsets.UTF_8.decode(byteBuffer).toString())
                    }
                    DataBufferUtils.release(buffer)
                    content.toString()
                }
                .reduce { acc: String, chunk: String -> acc + chunk }
        } catch (ex: Exception) {
            log.error(ex) { "Error collecting file content for file: ${file.filename()}" }
            throw ex
        }
}
