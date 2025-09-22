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
    }

    private val csvMapper =
        CsvMapperFactory
            .newInstance()
            .newMapper(Product::class.java)

    fun parseFileStreaming(filePart: FilePart): Flow<Product> =
        flow {
            log.info { "Starting CSV parsing for file: ${filePart.filename()}" }
            try {
                val content = collectFileContent(filePart)
                log.info { "File content collected, size: ${content.length} characters for file: ${filePart.filename()}" }

                var productCount = 0
                StringReader(content).use { reader ->
                    val iterator = csvMapper.iterator(reader)
                    while (iterator.hasNext()) {
                        val product = iterator.next()
                        productCount++
                        emit(product)

                        if (productCount % 100 == 0) {
                            log.debug { "Parsed $productCount products so far from file: ${filePart.filename()}" }
                        }
                    }
                }
                log.info { "CSV parsing completed for file: ${filePart.filename()}, total products parsed: $productCount" }
            } catch (ex: Exception) {
                log.error(ex) { "Error parsing CSV file: ${filePart.filename()}" }
                throw ex
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun collectFileContent(filePart: FilePart): String {
        log.debug { "Collecting file content for file: ${filePart.filename()}" }
        try {
            return filePart.content()
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
            log.error(ex) { "Error collecting file content for file: ${filePart.filename()}" }
            throw ex
        }
    }
}
