package com.albert.catalog.service

import com.albert.catalog.entity.Product
import kotlinx.coroutines.flow.Flow
import org.springframework.http.codec.multipart.FilePart

/**
 * Service responsible for processing CSV files and parsing them into Product entities.
 * Handles streaming CSV processing with batching for memory efficiency.
 */
fun interface CsvProcessingService {

    /**
     * Parses a CSV file into a flow of Product batches.
     * The first row of the CSV is treated as a header and skipped during processing.
     * Each batch contains a predefined number of parsed products.
     *
     * @param file The CSV file to be processed
     * @return A flow emitting lists of Product objects parsed from the CSV rows. Each list represents a batch.
     */
    fun parseProductsFromCsv(file: FilePart): Flow<List<Product>>
}
