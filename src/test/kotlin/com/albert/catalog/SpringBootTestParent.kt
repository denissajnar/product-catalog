package com.albert.catalog

import com.albert.catalog.config.TestcontainersConfiguration
import com.albert.catalog.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
abstract class SpringBootTestParent {

    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var productRepository: ProductRepository

    @AfterEach
    fun tearDown(): Unit = runBlocking {
        productRepository.deleteAll()
    }
}
