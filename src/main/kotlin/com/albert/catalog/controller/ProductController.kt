package com.albert.catalog.controller

import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.service.ProductService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Validated
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product API", description = "Product catalog management operations")
class ProductController(
    private val productService: ProductService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Operation(
        summary = "List all products with pagination",
        description = "Retrieves a paginated list of all products. Supports sorting and pagination parameters.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Products retrieved successfully",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = Pageable::class),
                    ),
                ],
            ),
        ],
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllProducts(
        @ParameterObject
        @PageableDefault(size = 20, sort = ["longName"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): ResponseEntity<Page<ProductResponse>> {
        log.info { "Retrieving products with pagination: page=${pageable.pageNumber}, size=${pageable.pageSize}, sort=${pageable.sort}" }
        val response = productService.getPagedProducts(pageable)
        log.info { "Retrieved ${response.content.size} products from page ${response.number} of ${response.totalPages}" }
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "Get product by ID",
        description = "Retrieves a specific product by its id identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Product found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProductResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "404", description = "Product not found", content = [Content()]),
        ],
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getProductByid(
        @Parameter(description = "Product id", required = true) @PathVariable id: Long,
    ): ResponseEntity<ProductResponse> {
        log.info { "Retrieving product with id: $id" }
        return productService.findById(id)
            ?.let {
                log.info { "Product found with id: $id" }
                ResponseEntity.ok(it)
            }
            ?: run {
                log.warn { "Product not found with id: $id" }
                ResponseEntity.notFound().build()
            }
    }

    @Operation(
        summary = "Update existing product",
        description = "Updates an existing product with the provided data.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Product updated successfully",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProductResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Invalid input data", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Product not found", content = [Content()]),
        ],
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun updateProduct(
        @Parameter(description = "Product id", required = true) @PathVariable id: Long,
        @Parameter(
            description = "Updated product data",
            required = true,
        ) @Valid @RequestBody productRequest: ProductRequest,
    ): ResponseEntity<ProductResponse> {
        log.info { "Updating product with id: $id" }
        return productService.update(id, productRequest)
            ?.let {
                log.info { "Product updated successfully with id: $id" }
                ResponseEntity.ok(it)
            }
            ?: run {
                log.warn { "Product not found for update with id: $id" }
                ResponseEntity.notFound().build()
            }
    }

    @Operation(
        summary = "Delete product",
        description = "Deletes a product by its id identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Product deleted successfully", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Product not found", content = [Content()]),
        ],
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    fun deleteProduct(
        @Parameter(description = "Product id", required = true) @PathVariable id: Long,
    ): ResponseEntity<Unit> {
        log.info { "Deleting product with id: $id" }
        return productService.delete(id)
            .takeIf { it }
            ?.let {
                log.info { "Product deleted successfully with id: $id" }
                ResponseEntity.noContent().build()
            }
            ?: run {
                log.warn { "Product not found for deletion with id: $id" }
                ResponseEntity.notFound().build()
            }
    }

    @Operation(
        summary = "Import products from CSV file",
        description = "Imports products from a CSV file. The CSV should have headers: uuid,gold_id,long_name,short_name,iow_unit_type,healthy_category.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Products imported successfully"),
            ApiResponse(responseCode = "400", description = "Invalid file format or content", content = [Content()]),
        ],
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importProducts(
        @Parameter(description = "CSV file containing products to import", required = true)
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<Unit> {
        log.info { "Starting product import from file: ${file.originalFilename}" }
        try {
            productService.importProducts(file)
            log.info { "Product import completed successfully for file: ${file.originalFilename}" }
            return ResponseEntity.status(201).build()
        } catch (ex: Exception) {
            log.error(ex) { "Product import failed for file: ${file.originalFilename}" }
            throw ex
        }
    }
}
