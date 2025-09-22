package com.albert.catalog.dto

/**
 * Data class to track import statistics during product import process.
 */
data class ImportStats(
    var imported: Int = 0,
    var skipped: Int = 0,
    val existingGoldIds: MutableSet<Long> = mutableSetOf(),
) {
    fun incrementImported() {
        imported++
    }

    fun incrementSkipped() {
        skipped++
    }

    fun addSkipped(count: Int) {
        skipped += count
    }
}
