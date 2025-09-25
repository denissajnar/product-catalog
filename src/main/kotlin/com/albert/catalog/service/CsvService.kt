package com.albert.catalog.service

import com.albert.catalog.dto.ImportStats
import com.albert.catalog.entity.Product
import org.springframework.web.multipart.MultipartFile

/**
 * Service interface for CSV processing operations.
 * Handles parsing CSV files and extracting product data.
 */
fun interface CsvService {

    /**
     * Processes a CSV file and returns a sequence of products with import statistics.
     *
     * @param file the CSV file containing product data to be imported
     * @param onBatch callback function to handle each batch of products
     * @return ImportStats containing the processing statistics
     */
    fun processProductsCsv(file: MultipartFile, onBatch: (List<Product>) -> Unit): ImportStats
}
