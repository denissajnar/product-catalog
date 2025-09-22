package com.albert.catalog.controller

import com.albert.catalog.SpringBootTestParent
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import java.time.LocalDateTime
import java.util.*

class ProductControllerTest : SpringBootTestParent() {

    @Test
    fun `should get empty products list initially`() {
        webTestClient
            .get()
            .uri("/api/v1/products")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
    }

    @Test
    fun `should create and retrieve product`(): Unit = runBlocking {
        val product = Product(
            uuid = null,
            goldId = 12345L,
            longName = "Test Product Long Name",
            shortName = "Test Product",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)

        webTestClient
            .get()
            .uri("/api/v1/products/${savedProduct.uuid}")
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody!!
            .let { response ->
                assert(response.goldId == 12345L)
                assert(response.longName == "Test Product Long Name")
                assert(response.shortName == "Test Product")
                assert(response.iowUnitType == "EACH")
                assert(response.healthyCategory == "GREEN")
            }
    }

    @Test
    fun `should return 404 for non-existent product`() {
        val nonExistentId = UUID.randomUUID()

        webTestClient
            .get()
            .uri("/api/v1/products/$nonExistentId")
            .exchange()
            .expectStatus().isNotFound
    }


    @Test
    fun `should update existing product`(): Unit = runBlocking {
        val product = Product(
            uuid = null,
            goldId = 11111L,
            longName = "Original Product",
            shortName = "Original",
            iowUnitType = "LITER",
            healthyCategory = "RED",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)

        val updateRequest = ProductRequest(
            goldId = 11111L,
            longName = "Updated Product Long Name",
            shortName = "Updated Product",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.uuid}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody!!
            .let { response ->
                assert(response.longName == "Updated Product Long Name")
                assert(response.shortName == "Updated Product")
                assert(response.healthyCategory == "GREEN")
            }
    }

    @Test
    fun `should delete existing product`(): Unit = runBlocking {
        val product = Product(
            uuid = null,
            goldId = 22222L,
            longName = "To Delete Product",
            shortName = "To Delete",
            iowUnitType = "EACH",
            healthyCategory = "AMBER",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)

        webTestClient
            .delete()
            .uri("/api/v1/products/${savedProduct.uuid}")
            .exchange()
            .expectStatus().isNoContent

        webTestClient
            .get()
            .uri("/api/v1/products/${savedProduct.uuid}")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return 404 when deleting non-existent product`() {
        val nonExistentId = UUID.randomUUID()

        webTestClient
            .delete()
            .uri("/api/v1/products/$nonExistentId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return 404 when updating non-existent product`() {
        val nonExistentId = UUID.randomUUID()
        val updateRequest = ProductRequest(
            goldId = 99999L,
            longName = "Non-existent Product",
            shortName = "Non-existent",
            iowUnitType = "PIECE",
            healthyCategory = "GREEN",
        )

        webTestClient
            .put()
            .uri("/api/v1/products/$nonExistentId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should get products with custom pagination`(): Unit = runBlocking {
        // Create some test products
        val products = listOf(
            Product(
                uuid = null,
                goldId = 11111L,
                longName = "Alpha Product",
                shortName = "Alpha",
                iowUnitType = "PIECE",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
            Product(
                uuid = null,
                goldId = 22222L,
                longName = "Beta Product",
                shortName = "Beta",
                iowUnitType = "PIECE",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
            Product(
                uuid = null,
                goldId = 33333L,
                longName = "Gamma Product",
                shortName = "Gamma",
                iowUnitType = "PIECE",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )

        productRepository.saveAll(products).collectList().awaitSingle()

        webTestClient
            .get()
            .uri("/api/v1/products?page=0&size=2&sort=longName,asc")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(2)
            .jsonPath("$.content[0].longName").isEqualTo("Alpha Product")
            .jsonPath("$.content[1].longName").isEqualTo("Beta Product")
    }

    @Test
    fun `should import products from CSV file`() {
        val csvContent = """uuid,gold_id,long_name,short_name,iow_unit_type,healthy_category
d3cd0675-a1f7-4aa6-835c-8191eae20343,72933054,NŮŽKY VELKÉ KOV.,NŮŽKY VELKÉ KOV.,PIECE,Other
e470ba23-af86-48a3-9519-f3cc0493e860,73842336,UNI B&J PEANUT BUTTER 500ML,UNI B&J PEAN.BUTT500,PIECE,Other"""

        webTestClient
            .post()
            .uri("/api/v1/products/import")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData("file", csvContent))
            .exchange()
            .expectStatus().isEqualTo(201)

        // Verify products were imported by checking if we can retrieve them
        webTestClient
            .get()
            .uri("/api/v1/products")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0))
    }
}
