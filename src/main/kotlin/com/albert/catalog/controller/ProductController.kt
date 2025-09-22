package com.albert.catalog.controller

import com.albert.catalog.dto.ProductPageResponse
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.service.ProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*

@Validated
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product API", description = "Product catalog management operations")
class ProductController(
    private val productService: ProductService,
) {

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
                        schema = Schema(implementation = ProductPageResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getAllProducts(
        @Parameter(description = "Pagination and sorting parameters")
        @PageableDefault(size = 20, sort = ["longName"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): ResponseEntity<ProductPageResponse> =
        ResponseEntity.ok(productService.getPagedProducts(pageable))

    @Operation(
        summary = "Get product by ID",
        description = "Retrieves a specific product by its UUID identifier.",
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
    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getProductById(
        @Parameter(description = "Product UUID", required = true) @PathVariable id: UUID,
    ): ResponseEntity<ProductResponse> =
        productService.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

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
    @PutMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun updateProduct(
        @Parameter(description = "Product UUID", required = true) @PathVariable id: UUID,
        @Parameter(
            description = "Updated product data",
            required = true,
        ) @Valid @RequestBody productRequest: ProductRequest,
    ): ResponseEntity<ProductResponse> =
        productService.update(id, productRequest)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @Operation(
        summary = "Delete product",
        description = "Deletes a product by its UUID identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Product deleted successfully", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Product not found", content = [Content()]),
        ],
    )
    @DeleteMapping("/{id}")
    suspend fun deleteProduct(
        @Parameter(description = "Product UUID", required = true) @PathVariable id: UUID,
    ): ResponseEntity<Unit> =
        productService.delete(id)
            .takeIf { it }
            ?.let { ResponseEntity.noContent().build() }
            ?: ResponseEntity.notFound().build()

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
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun importProducts(
        @Parameter(description = "CSV file containing products to import", required = true)
        @RequestPart("file") file: FilePart,
    ): ResponseEntity<Unit> {
        productService.importProducts(file)

        return ResponseEntity.status(201).build()
    }
}
