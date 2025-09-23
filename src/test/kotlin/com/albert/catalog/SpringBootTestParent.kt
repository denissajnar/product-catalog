package com.albert.catalog

import com.albert.catalog.config.TestcontainersConfiguration
import com.albert.catalog.repository.ProductRepository
import io.restassured.RestAssured
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
abstract class SpringBootTestParent {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var productRepository: ProductRepository

    @BeforeEach
    fun setUp() {
        RestAssured.port = port
        RestAssured.baseURI = "http://localhost"
        RestAssured.basePath = ""
    }

    @AfterEach
    fun tearDown() {
        productRepository.deleteAll()
    }
}
