package com.albert.catalog.controller

import com.albert.catalog.SpringBootTestParent
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.entity.Product
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters
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
            uuid = UUID.randomUUID(),
            goldId = 12345L,
            longName = "Test Product Long Name",
            shortName = "Test Product",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)
        val assertions = SoftAssertions()

        val response = webTestClient
            .get()
            .uri("/api/v1/products/${savedProduct.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody

        assertions.assertThat(response).isNotNull

        response?.let {
            assertions.assertThat(it.goldId).isEqualTo(12345L)
            assertions.assertThat(it.longName).isEqualTo("Test Product Long Name")
            assertions.assertThat(it.shortName).isEqualTo("Test Product")
            assertions.assertThat(it.iowUnitType).isEqualTo("EACH")
            assertions.assertThat(it.healthyCategory).isEqualTo("GREEN")
        }

        assertions.assertAll()
    }

    @Test
    fun `should return 404 for non-existent product`() {
        val nonExistentId = 99999L

        webTestClient
            .get()
            .uri("/api/v1/products/$nonExistentId")
            .exchange()
            .expectStatus().isNotFound
    }


    @Test
    fun `should update existing product`(): Unit = runBlocking {
        val product = Product(
            uuid = UUID.randomUUID(),
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

        val assertions = SoftAssertions()
        val response = webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody

        assertions.assertThat(response).isNotNull

        response?.let {
            assertions.assertThat(it.longName).isEqualTo("Updated Product Long Name")
            assertions.assertThat(it.shortName).isEqualTo("Updated Product")
            assertions.assertThat(it.healthyCategory).isEqualTo("GREEN")
        }

        assertions.assertAll()
    }

    @Test
    fun `should delete existing product`(): Unit = runBlocking {
        val product = Product(
            uuid = UUID.randomUUID(),
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
            .uri("/api/v1/products/${savedProduct.id}")
            .exchange()
            .expectStatus().isNoContent

        webTestClient
            .get()
            .uri("/api/v1/products/${savedProduct.id}")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return 404 when deleting non-existent product`() {
        val nonExistentId = 99998L

        webTestClient
            .delete()
            .uri("/api/v1/products/$nonExistentId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return 404 when updating non-existent product`() {
        val nonExistentId = 99997L
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
        val products = listOf(
            Product(
                uuid = UUID.randomUUID(),
                goldId = 11111L,
                longName = "Alpha Product",
                shortName = "Alpha",
                iowUnitType = "PIECE",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
            Product(
                uuid = UUID.randomUUID(),
                goldId = 22222L,
                longName = "Beta Product",
                shortName = "Beta",
                iowUnitType = "PIECE",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
            Product(
                uuid = UUID.randomUUID(),
                goldId = 33333L,
                longName = "Gamma Product",
                shortName = "Gamma",
                iowUnitType = "PIECE",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )

        productRepository.save(products[0])
        productRepository.save(products[1])
        productRepository.save(products[2])

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
        val csvFile = ClassPathResource("product_import_anonymized.csv")

        val multipartBuilder = MultipartBodyBuilder()
        multipartBuilder.part("file", csvFile)
            .filename("product_import_anonymized.csv")
            .contentType(MediaType.TEXT_PLAIN)

        webTestClient
            .post()
            .uri("/api/v1/products/import")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartBuilder.build()))
            .exchange()
            .expectStatus().isEqualTo(201)

        webTestClient
            .get()
            .uri("/api/v1/products")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").value(Matchers.greaterThan(0))
    }

    @Test
    fun `should return 400 when updating product with invalid goldId`(): Unit = runBlocking {
        val product = Product(
            uuid = UUID.randomUUID(),
            goldId = 12345L,
            longName = "Test Product",
            shortName = "Test",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)

        val invalidRequest = ProductRequest(
            goldId = -1L, // Invalid: negative goldId
            longName = "Updated Product",
            shortName = "Updated",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
        )

        webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when updating product with blank longName`(): Unit = runBlocking {
        val product = Product(
            uuid = UUID.randomUUID(),
            goldId = 12345L,
            longName = "Test Product",
            shortName = "Test",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)

        val invalidRequest = ProductRequest(
            goldId = 12345L,
            longName = "  ", // Invalid: blank longName
            shortName = "Updated",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
        )

        webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when updating product with blank shortName`(): Unit = runBlocking {
        val product = Product(
            uuid = UUID.randomUUID(),
            goldId = 12345L,
            longName = "Test Product",
            shortName = "Test",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(product)

        val invalidRequest = ProductRequest(
            goldId = 12345L,
            longName = "Updated Product",
            shortName = "", // Invalid: empty shortName
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
        )

        webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should get products with different sorting options`(): Unit = runBlocking {
        val products = listOf(
            Product(
                uuid = UUID.randomUUID(),
                goldId = 30000L,
                longName = "Zebra Product",
                shortName = "Zebra",
                iowUnitType = "PIECE",
                healthyCategory = "RED",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
            Product(
                uuid = UUID.randomUUID(),
                goldId = 10000L,
                longName = "Alpha Product",
                shortName = "Alpha",
                iowUnitType = "EACH",
                healthyCategory = "GREEN",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )

        productRepository.save(products[0])
        productRepository.save(products[1])

        // Test sort by goldId ascending
        webTestClient
            .get()
            .uri("/api/v1/products?sort=goldId,asc")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content[0].goldId").isEqualTo(10000)
            .jsonPath("$.content[1].goldId").isEqualTo(30000)

        // Test sort by goldId descending
        webTestClient
            .get()
            .uri("/api/v1/products?sort=goldId,desc")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content[0].goldId").isEqualTo(30000)
            .jsonPath("$.content[1].goldId").isEqualTo(10000)

        // Test sort by shortName ascending
        webTestClient
            .get()
            .uri("/api/v1/products?sort=shortName,asc")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content[0].shortName").isEqualTo("Alpha")
            .jsonPath("$.content[1].shortName").isEqualTo("Zebra")
    }

    @Test
    fun `should handle edge cases for pagination`(): Unit = runBlocking {
        val product = Product(
            uuid = UUID.randomUUID(),
            goldId = 99999L,
            longName = "Single Product",
            shortName = "Single",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        productRepository.save(product)

        // Test page beyond available data
        webTestClient
            .get()
            .uri("/api/v1/products?page=999&size=10")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.page").isEqualTo(999)
            .jsonPath("$.totalElements").isEqualTo(1)

        // Test with very large page size
        webTestClient
            .get()
            .uri("/api/v1/products?page=0&size=1000")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.size").isEqualTo(1000)
            .jsonPath("$.totalElements").isEqualTo(1)
    }

    @Test
    fun `should return 404 for invalid ID formats`() {
        // Test with zero ID
        webTestClient
            .get()
            .uri("/api/v1/products/0")
            .exchange()
            .expectStatus().isNotFound

        // Test with negative ID
        webTestClient
            .get()
            .uri("/api/v1/products/-1")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should verify product data consistency after multiple operations`(): Unit = runBlocking {
        val originalProduct = Product(
            uuid = UUID.randomUUID(),
            goldId = 55555L,
            longName = "Consistency Test Product",
            shortName = "Consistency",
            iowUnitType = "LITER",
            healthyCategory = "AMBER",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val savedProduct = productRepository.save(originalProduct)

        // First update
        val firstUpdate = ProductRequest(
            goldId = 55555L,
            longName = "First Update",
            shortName = "First",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        val assertions = SoftAssertions()
        val firstResponse = webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(firstUpdate)
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody

        assertions.assertThat(firstResponse).isNotNull

        firstResponse?.let {
            assertions.assertThat(it.longName).isEqualTo("First Update")
            assertions.assertThat(it.shortName).isEqualTo("First")
            assertions.assertThat(it.healthyCategory).isEqualTo("GREEN")
        }

        // Second update
        val secondUpdate = ProductRequest(
            goldId = 55555L,
            longName = "Second Update",
            shortName = "Second",
            iowUnitType = "EACH",
            healthyCategory = "RED",
        )

        val secondResponse = webTestClient
            .put()
            .uri("/api/v1/products/${savedProduct.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(secondUpdate)
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody

        assertions.assertThat(secondResponse).isNotNull

        secondResponse?.let {
            assertions.assertThat(it.longName).isEqualTo("Second Update")
            assertions.assertThat(it.shortName).isEqualTo("Second")
            assertions.assertThat(it.iowUnitType).isEqualTo("EACH")
            assertions.assertThat(it.healthyCategory).isEqualTo("RED")
        }

        // Verify final state by retrieving the product
        val finalResponse = webTestClient
            .get()
            .uri("/api/v1/products/${savedProduct.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductResponse>()
            .returnResult()
            .responseBody

        assertions.assertThat(finalResponse).isNotNull

        finalResponse?.let {
            assertions.assertThat(it.longName).isEqualTo("Second Update")
            assertions.assertThat(it.shortName).isEqualTo("Second")
            assertions.assertThat(it.iowUnitType).isEqualTo("EACH")
            assertions.assertThat(it.healthyCategory).isEqualTo("RED")
        }

        assertions.assertAll()
    }
}
