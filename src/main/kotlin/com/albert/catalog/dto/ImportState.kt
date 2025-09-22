package com.albert.catalog.dto

import com.albert.catalog.entity.Product
import com.albert.catalog.service.ProductServiceImpl.Companion.DEFAULT_BATCH_SIZE

/**
 * State holder for import process
 */
data class ImportState(
    var totalProcessed: Int = 0,
    var savedCount: Int = 0,
    var updatedCount: Int = 0,
    var errorCount: Int = 0,
    val batch: MutableList<Product> = mutableListOf(),
) {
    fun updateCounts(batchResult: BatchResult) {
        savedCount += batchResult.saved
        updatedCount += batchResult.updated
        errorCount += batchResult.errors
    }

    fun getBatchNumber(): Int = (totalProcessed / DEFAULT_BATCH_SIZE) + 1
}
