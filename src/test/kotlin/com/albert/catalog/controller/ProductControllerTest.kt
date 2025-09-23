package com.albert.catalog.controller

import com.albert.catalog.SpringBootTestParent
import com.albert.catalog.dto.ProductRequest
import com.albert.catalog.dto.ProductResponse
import com.albert.catalog.factory.createProduct
import com.albert.catalog.util.takeAs
import com.albert.catalog.util.whenever
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import java.io.File

class ProductControllerTest : SpringBootTestParent() {

    @Test
    fun `should get empty products list initially`() {
        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", hasSize<Any>(0))
            .body("totalElements", equalTo(0))
            .body("number", equalTo(0))
            .body("size", equalTo(20))
    }

    @Test
    fun `should create and retrieve product`() {
        val product = createProduct()
        val savedProduct = productRepository.save(product)

        val response = given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .takeAs<ProductResponse>()

        assertThat(response).isNotNull()
        assertThat(response.goldId).isEqualTo(product.goldId)
        assertThat(response.longName).isEqualTo(product.longName)
        assertThat(response.shortName).isEqualTo(product.shortName)
        assertThat(response.iowUnitType).isEqualTo(product.iowUnitType)
        assertThat(response.healthyCategory).isEqualTo(product.healthyCategory)
    }

    @Test
    fun `should return 404 for non-existent product`() {
        val nonExistentId = 99999L

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products/$nonExistentId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `should update existing product`() {
        val product = createProduct(
            goldId = 11111L,
            longName = "Original Product",
            shortName = "Original",
            iowUnitType = "LITER",
            healthyCategory = "RED",
        )
        val savedProduct = productRepository.save(product)

        val updateRequest = ProductRequest(
            goldId = 11111L,
            longName = "Updated Product Long Name",
            shortName = "Updated Product",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        val response = given()
            .auth().basic("admin", "admin123")
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .whenever()
            .put("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .takeAs<ProductResponse>()

        val assertions = SoftAssertions()
        assertions.assertThat(response).isNotNull
        assertions.assertThat(response.longName).isEqualTo("Updated Product Long Name")
        assertions.assertThat(response.shortName).isEqualTo("Updated Product")
        assertions.assertThat(response.healthyCategory).isEqualTo("GREEN")
        assertions.assertAll()
    }

    @Test
    fun `should delete existing product`() {
        val product = createProduct(
            goldId = 22222L,
            longName = "To Delete Product",
            shortName = "To Delete",
            iowUnitType = "EACH",
            healthyCategory = "AMBER",
        )
        val savedProduct = productRepository.save(product)

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .delete("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(204)

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `should return 404 when deleting non-existent product`() {
        val nonExistentId = 99999L

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .delete("/api/v1/products/$nonExistentId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `should return 404 when updating non-existent product`() {
        val nonExistentId = 99999L
        val updateRequest = ProductRequest(
            goldId = 11111L,
            longName = "Updated Product",
            shortName = "Updated",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        given()
            .auth().basic("admin", "admin123")
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .whenever()
            .put("/api/v1/products/$nonExistentId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `should get products with custom pagination`() {
        val products = listOf(
            createProduct(goldId = 1L, longName = "Apple"),
            createProduct(goldId = 2L, longName = "Banana"),
            createProduct(goldId = 3L, longName = "Cherry"),
        )
        productRepository.saveAll(products)

        given()
            .auth().basic("admin", "admin123")
            .queryParam("size", 2)
            .queryParam("page", 0)
            .queryParam("sort", "longName,desc")
            .whenever()
            .get("/api/v1/products")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", hasSize<Any>(2))
            .body("totalElements", equalTo(3))
            .body("number", equalTo(0))
            .body("size", equalTo(2))
    }

    @Test
    fun `should import products from CSV file`() {
        val csvContent = """uuid,gold_id,long_name,short_name,iow_unit_type,healthy_category
550e8400-e29b-41d4-a716-446655440000,12345,Test Product 1,Test1,EACH,GREEN
550e8400-e29b-41d4-a716-446655440001,12346,Test Product 2,Test2,LITER,AMBER"""

        val csvFile = File.createTempFile("test_products", ".csv")
        csvFile.writeText(csvContent)

        given()
            .auth().basic("admin", "admin123")
            .multiPart("file", csvFile, "text/csv")
            .whenever()
            .post("/api/v1/products/import")
            .then()
            .statusCode(201)

        csvFile.delete()
    }

    @Test
    fun `should return 400 when updating product with invalid goldId`() {
        val product = createProduct()
        val savedProduct = productRepository.save(product)

        val invalidUpdateRequest = ProductRequest(
            goldId = -1L, // Invalid goldId
            longName = "Updated Product",
            shortName = "Updated",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        given()
            .auth().basic("admin", "admin123")
            .contentType(ContentType.JSON)
            .body(invalidUpdateRequest)
            .whenever()
            .put("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(400)
    }

    @Test
    fun `should return 400 when updating product with blank longName`() {
        val product = createProduct()
        val savedProduct = productRepository.save(product)

        val invalidUpdateRequest = ProductRequest(
            goldId = 12345L,
            longName = "",
            shortName = "Updated",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        given()
            .auth().basic("admin", "admin123")
            .contentType(ContentType.JSON)
            .body(invalidUpdateRequest)
            .whenever()
            .put("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(400)
    }

    @Test
    fun `should return 400 when updating product with blank shortName`() {
        val product = createProduct()
        val savedProduct = productRepository.save(product)

        val invalidUpdateRequest = ProductRequest(
            goldId = 12345L,
            longName = "Updated Product",
            shortName = "",
            iowUnitType = "LITER",
            healthyCategory = "GREEN",
        )

        given()
            .auth().basic("admin", "admin123")
            .contentType(ContentType.JSON)
            .body(invalidUpdateRequest)
            .whenever()
            .put("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(400)
    }

    @Test
    fun `should get products with different sorting options`() {
        val products = listOf(
            createProduct(goldId = 1L, longName = "Apple", shortName = "A"),
            createProduct(goldId = 2L, longName = "Banana", shortName = "B"),
            createProduct(goldId = 3L, longName = "Cherry", shortName = "C"),
        )
        productRepository.saveAll(products)

        given()
            .auth().basic("admin", "admin123")
            .queryParam("sort", "longName,asc")
            .whenever()
            .get("/api/v1/products")
            .then()
            .statusCode(200)
            .body("content[0].longName", equalTo("Apple"))

        given()
            .auth().basic("admin", "admin123")
            .queryParam("sort", "longName,desc")
            .whenever()
            .get("/api/v1/products")
            .then()
            .statusCode(200)
            .body("content[0].longName", equalTo("Cherry"))
    }

    @Test
    fun `should handle edge cases for pagination`() {
        val product = createProduct()
        productRepository.save(product)

        given()
            .auth().basic("admin", "admin123")
            .queryParam("page", 10)
            .queryParam("size", 20)
            .whenever()
            .get("/api/v1/products")
            .then()
            .statusCode(200)
            .body("content", hasSize<Any>(0))
            .body("totalElements", equalTo(1))
            .body("number", equalTo(10))
    }

    @Test
    fun `should return 404 for invalid ID formats`() {
        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products/invalid")
            .then()
            .statusCode(400)
    }

    @Test
    fun `should verify product data consistency after multiple operations`() {
        val initialProduct = createProduct(
            goldId = 50001L,
            longName = "Consistency Test Product",
            shortName = "Consistency",
            iowUnitType = "EACH",
            healthyCategory = "GREEN",
        )
        val savedProduct = productRepository.save(initialProduct)

        val createdResponse = given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(200)
            .extract()
            .takeAs<ProductResponse>()

        assertThat(createdResponse.goldId).isEqualTo(50001L)
        assertThat(createdResponse.longName).isEqualTo("Consistency Test Product")

        val updateRequest = ProductRequest(
            goldId = 50002L,
            longName = "Updated Consistency Product",
            shortName = "Updated Consistency",
            iowUnitType = "LITER",
            healthyCategory = "AMBER",
        )

        val updatedResponse = given()
            .auth().basic("admin", "admin123")
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .whenever()
            .put("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(200)
            .extract()
            .takeAs<ProductResponse>()

        val assertions = SoftAssertions()
        assertions.assertThat(updatedResponse.goldId).isEqualTo(50002L)
        assertions.assertThat(updatedResponse.longName).isEqualTo("Updated Consistency Product")
        assertions.assertThat(updatedResponse.shortName).isEqualTo("Updated Consistency")
        assertions.assertThat(updatedResponse.iowUnitType).isEqualTo("LITER")
        assertions.assertThat(updatedResponse.healthyCategory).isEqualTo("AMBER")
        assertions.assertThat(updatedResponse.updatedAt).isNotNull()
        assertions.assertAll()

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products")
            .then()
            .statusCode(200)
            .body("content.find { it.goldId == 50002 }.longName", equalTo("Updated Consistency Product"))

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .delete("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(204)

        given()
            .auth().basic("admin", "admin123")
            .whenever()
            .get("/api/v1/products/${savedProduct.id}")
            .then()
            .statusCode(404)
    }
}
